-- =============================================================================
-- V3 · Catálogo (Fase 2): estado editorial, slug, tipo, precios DECIMAL,
--      SKUs (product_skus) e imágenes (product_images) con migración de datos.
--
-- No destructivo: no se borra ninguna columna ni tabla. products.available,
-- products.stock y product_childs siguen existiendo (capa de compatibilidad).
--
-- Idempotente y segura en base nueva: cada sentencia comprueba information_schema
-- y solo actúa si hace falta. En una base vacía no hace nada (Hibernate crea las
-- tablas ya con la estructura nueva).
-- =============================================================================

SET @has_products := (SELECT COUNT(*) FROM information_schema.TABLES
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products');

SET @has_children := (SELECT COUNT(*) FROM information_schema.TABLES
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_childs');

-- ---------------------------------------------------------------------------
-- 1. products.status  (available=1 → ACTIVE, 0 → INACTIVE)
-- ---------------------------------------------------------------------------
SET @missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'status');

SET @sql := IF(@has_products = 1 AND @missing = 1,
  'UPDATE products SET status = IF(available = 1, ''ACTIVE'', ''INACTIVE'')', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. products.product_type  (COMBO si is_combo, VARIANT si tiene variantes, si no SIMPLE)
-- ---------------------------------------------------------------------------
SET @missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'product_type');

SET @sql := IF(@has_products = 1 AND @missing = 1 AND @has_children = 1,
  'UPDATE products p SET p.product_type = CASE
       WHEN p.is_combo = 1 THEN ''COMBO''
       WHEN EXISTS (SELECT 1 FROM product_childs c WHERE c.product_id = p.id_product AND c.deleted = 0) THEN ''VARIANT''
       ELSE ''SIMPLE'' END', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_products = 1 AND @missing = 1 AND @has_children = 0,
  'UPDATE products SET product_type = IF(is_combo = 1, ''COMBO'', ''SIMPLE'')', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 3. products.slug  (nombre sin tildes ni símbolos + "-<id>" para garantizar unicidad)
-- ---------------------------------------------------------------------------
SET @missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'slug');

SET @sql := IF(@has_products = 1,
  'UPDATE products SET slug = CONCAT(
       LEFT(TRIM(BOTH ''-'' FROM REGEXP_REPLACE(
           REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
               LOWER(name), ''á'',''a''), ''é'',''e''), ''í'',''i''), ''ó'',''o''), ''ú'',''u''), ''ü'',''u''), ''ñ'',''n''),
           ''[^a-z0-9]+'', ''-'')), 160),
       ''-'', id_product)
   WHERE slug IS NULL OR slug = ''''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5a. Un SKU por variante existente
SET @sql := IF(@has_products = 1 AND @has_children = 1,
  'INSERT INTO product_skus (product_id, code, name, description, price, cost_price, weight, length, width, height,
                             image_url, is_default, active, legacy_child_id, version, usr_crea, created_at, deleted)
   SELECT c.product_id, CONCAT(''SKU-'', LPAD(c.product_id, 6, ''0''), ''-'', c.id_product_child), c.name, c.description,
          c.unit_price, p.real_price, p.weight, p.length, p.width, p.height,
          c.image_url, 0, c.available, c.id_product_child, 0, 1, NOW(6), c.deleted
   FROM product_childs c
   JOIN products p ON p.id_product = c.product_id
   WHERE NOT EXISTS (SELECT 1 FROM product_skus s WHERE s.legacy_child_id = c.id_product_child)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5b. SKU por defecto para productos sin variantes activas (simples y combos)
SET @sql := IF(@has_products = 1,
  'INSERT INTO product_skus (product_id, code, name, description, price, cost_price, weight, length, width, height,
                             image_url, is_default, active, legacy_child_id, version, usr_crea, created_at, deleted)
   SELECT p.id_product, CONCAT(''SKU-'', LPAD(p.id_product, 6, ''0'')), p.name, p.description,
          p.unit_price, p.real_price, p.weight, p.length, p.width, p.height,
          p.image_url, 1, p.available, NULL, 0, 1, NOW(6), p.deleted
   FROM products p
   WHERE p.product_type <> ''VARIANT''
     AND NOT EXISTS (SELECT 1 FROM product_skus s WHERE s.product_id = p.id_product AND s.legacy_child_id IS NULL)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5c. En productos con variantes, el SKU de menor id es el predeterminado
SET @sql := IF(@has_products = 1,
  'UPDATE product_skus s
   JOIN (SELECT product_id, MIN(id_sku) AS first_sku FROM product_skus
         WHERE legacy_child_id IS NOT NULL AND deleted = 0 GROUP BY product_id) f ON f.first_sku = s.id_sku
   SET s.is_default = 1', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6a. Imagen principal del producto (posición 0)
SET @sql := IF(@has_products = 1,
  'INSERT INTO product_images (product_id, sku_id, url, position, alt_text, usr_crea, created_at, deleted)
   SELECT p.id_product, NULL, p.image_url, 0, p.name, 1, NOW(6), p.deleted
   FROM products p
   WHERE p.image_url IS NOT NULL AND p.image_url <> ''''
     AND NOT EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = p.id_product AND i.sku_id IS NULL AND i.position = 0)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6b. Imagen de cada variante, ligada a su SKU
SET @sql := IF(@has_products = 1 AND @has_children = 1,
  'INSERT INTO product_images (product_id, sku_id, url, position, alt_text, usr_crea, created_at, deleted)
   SELECT s.product_id, s.id_sku, c.image_url, 1, c.name, 1, NOW(6), c.deleted
   FROM product_childs c
   JOIN product_skus s ON s.legacy_child_id = c.id_product_child
   WHERE c.image_url IS NOT NULL AND c.image_url <> ''''
     AND NOT EXISTS (SELECT 1 FROM product_images i WHERE i.sku_id = s.id_sku)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
