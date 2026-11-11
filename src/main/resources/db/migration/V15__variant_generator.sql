-- =============================================================================
-- V15 · Generador de variantes
--
--   product_variant_axes   ejes de variante de cada producto (los de la plantilla y los
--                          que se agregan solo a ese producto), con su orden y cuál define
--                          la imagen (p. ej. el color).
--   product_option_images  imagen de un valor de eje en un producto (Rojo → foto roja); se
--                          aplica a todas las variantes con ese valor.
--   product_childs         cost_price: costo propio de la variante (si es nulo se usa el
--                          costo del producto, como hasta ahora).
--   Los productos que ya tienen opciones en sus SKU reciben esos ejes en el orden de la
--   plantilla.
--
-- No destructivo e idempotente.
-- =============================================================================

-- Ejes ya usados en los SKU de cada producto (orden: posición en la plantilla, luego id).
SET @has_values := (SELECT COUNT(DISTINCT TABLE_NAME) = 4 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME IN ('sku_attribute_values', 'product_skus', 'products', 'template_attributes'));

SET @sql := IF(@has_values = 1,
  'INSERT IGNORE INTO product_variant_axes (product_id, attribute_id, position, image_axis)
   SELECT k.product_id, v.attribute_id, MIN(COALESCE(ta.position, 100 + v.attribute_id)), 0
     FROM sku_attribute_values v
     JOIN product_skus k ON k.id_sku = v.sku_id AND k.deleted = 0
     JOIN products p ON p.id_product = k.product_id
     LEFT JOIN template_attributes ta ON ta.template_id = p.template_id AND ta.attribute_id = v.attribute_id
    GROUP BY k.product_id, v.attribute_id', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
