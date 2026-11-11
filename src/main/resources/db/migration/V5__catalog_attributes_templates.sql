-- =============================================================================
-- V5 · Atributos, plantillas y ejes de variante (Fase 4)
--
-- Modelo nuevo (catálogo):
--   attributes            definición tipada (TEXT | NUMBER | BOOLEAN | OPTION) + unidad
--   attribute_options     valores permitidos de un atributo OPTION (color: Rojo, Azul…)
--   product_templates     plantilla por tipo de producto (antes type_products)
--   template_attributes   atributos de la plantilla: obligatorio, eje de variante, filtrable
--   product_attribute_values  ficha técnica del producto (un valor tipado por atributo)
--   sku_attribute_values      opción de cada eje de variante para cada SKU
--   products.template_id
--
-- Migra los datos de features / type_products / type_product_features /
-- product_features (las tablas antiguas NO se borran: siguen sirviendo a los
-- endpoints /product_details/** marcados como obsoletos hasta la Fase 7).
--
-- No destructivo e idempotente; en base vacía solo crea las tablas.
-- =============================================================================

SET @has_products := (SELECT COUNT(*) FROM information_schema.TABLES
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products');

SET @has_skus := (SELECT COUNT(*) FROM information_schema.TABLES
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_skus');

SET @has_features := (SELECT COUNT(*) FROM information_schema.TABLES
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'features');

SET @has_feature_units := (SELECT COUNT(*) FROM information_schema.TABLES
                           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feature_unit_measurement');

SET @has_types := (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'type_products');

SET @has_type_features := (SELECT COUNT(*) FROM information_schema.TABLES
                           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'type_product_features');

SET @has_product_features := (SELECT COUNT(*) FROM information_schema.TABLES
                              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_features');

SET @ready := (@has_products = 1 AND @has_skus = 1);

-- 8. Migración de datos ------------------------------------------------------------
-- 8.1 features → attributes (NUMBER: el valor antiguo era numérico).
--     code: nombre en minúsculas con "_"; si se repite se le añade _<id>.
-- Fila a fila: código base + sufijo _<id> si ya existe, para no fallar por nombres parecidos.
DROP TABLE IF EXISTS v5_tmp_feature_codes;

SET @sql := IF(@ready = 1 AND @has_features = 1,
  'CREATE TABLE v5_tmp_feature_codes AS
   SELECT f.id_feature,
          LEFT(f.name, 120) AS name,
          NULLIF(LEFT(TRIM(BOTH ''_'' FROM REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(f.name), ''á'', ''a''), ''é'', ''e''), ''í'', ''i''), ''ó'', ''o''), ''ú'', ''u''), ''ü'', ''u''), ''ñ'', ''n''), ''[^a-z0-9]+'', ''_'')), 48), '''') AS base_code
     FROM features f
    WHERE f.deleted = 0
      AND NOT EXISTS (SELECT 1 FROM attributes a WHERE a.legacy_feature_id = f.id_feature)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@ready = 1 AND @has_features = 1,
  'INSERT INTO attributes (code, name, data_type, legacy_feature_id, usr_crea, created_at, deleted)
   SELECT CASE
            WHEN t.base_code IS NULL THEN CONCAT(''attr_'', t.id_feature)
            WHEN EXISTS (SELECT 1 FROM attributes a WHERE a.code_active = t.base_code)
              OR EXISTS (SELECT 1 FROM v5_tmp_feature_codes t2 WHERE t2.base_code = t.base_code AND t2.id_feature < t.id_feature)
              THEN CONCAT(t.base_code, ''_'', t.id_feature)
            ELSE t.base_code
          END,
          t.name, ''NUMBER'', t.id_feature, 1, NOW(6), 0
     FROM v5_tmp_feature_codes t
    ORDER BY t.id_feature', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS v5_tmp_feature_codes;

-- 8.2 Unidad: la primera unidad asociada a la característica.
SET @sql := IF(@ready = 1 AND @has_feature_units = 1,
  'UPDATE attributes a
      JOIN (SELECT id_feature, MIN(id_unit_measurement) AS unit_id
              FROM feature_unit_measurement GROUP BY id_feature) fu
        ON fu.id_feature = a.legacy_feature_id
      SET a.unit_id = fu.unit_id
    WHERE a.unit_id IS NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 8.3 type_products → product_templates.
SET @sql := IF(@ready = 1 AND @has_types = 1,
  'INSERT INTO product_templates (name, legacy_type_product_id, usr_crea, created_at, deleted)
   SELECT LEFT(tp.name, 120), tp.id_type_product, 1, NOW(6), 0
     FROM type_products tp
    WHERE tp.deleted = 0
      AND NOT EXISTS (SELECT 1 FROM product_templates pt WHERE pt.legacy_type_product_id = tp.id_type_product)
      AND NOT EXISTS (SELECT 1 FROM product_templates pt WHERE pt.name_active = LEFT(tp.name, 120))', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 8.4 type_product_features → template_attributes (filtrable por defecto).
SET @sql := IF(@ready = 1 AND @has_type_features = 1,
  'INSERT IGNORE INTO template_attributes (template_id, attribute_id, required, variant_axis, filterable, position)
   SELECT pt.id_template, a.id_attribute, 0, 0, 1, 0
     FROM type_product_features tpf
     JOIN product_templates pt ON pt.legacy_type_product_id = tpf.type_product_id AND pt.deleted = 0
     JOIN attributes a ON a.legacy_feature_id = tpf.feature_id AND a.deleted = 0
    WHERE tpf.deleted = 0', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 8.5 product_features → product_attribute_values (valor numérico).
SET @sql := IF(@ready = 1 AND @has_product_features = 1,
  'INSERT IGNORE INTO product_attribute_values (product_id, attribute_id, value_number)
   SELECT pf.product_id, a.id_attribute, pf.value
     FROM product_features pf
     JOIN attributes a ON a.legacy_feature_id = pf.feature_id AND a.deleted = 0
     JOIN products p ON p.id_product = pf.product_id
    WHERE pf.deleted = 0', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
