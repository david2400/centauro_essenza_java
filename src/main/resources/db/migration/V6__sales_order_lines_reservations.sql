-- =============================================================================
-- V6 · Ventas por SKU y reservas de stock (Fase 5)
--
--   orders          total DECIMAL(15,2); stock_managed (las órdenes nuevas reservan y
--                   descuentan stock; las existentes no se tocan); cancel_reason;
--                   estado normalizado a mayúsculas.
--   product_orders  dinero DECIMAL(15,2); sku_id + foto del precio, código y nombre
--                   (sku_code, product_name, unit_price) congelada al crear la línea.
--   stock_reservations  reservas por línea y bodega (ACTIVE → COMMITTED / RELEASED /
--                   EXPIRED; COMMITTED → RETURNED si se cancela una orden pagada).
--
-- No destructivo e idempotente.
-- =============================================================================

SET @has_orders := (SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders');

SET @has_lines := (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_orders');

SET @has_skus := (SELECT COUNT(*) FROM information_schema.TABLES
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_skus');

SET @ready := (@has_orders = 1 AND @has_lines = 1 AND @has_skus = 1);

SET @sql := IF(@ready = 1, 'UPDATE orders SET state = UPPER(TRIM(state)) WHERE state <> UPPER(TRIM(state))', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Líneas existentes: SKU por defecto del producto (si lo tiene), nombre y precio unitario
-- deducido de la línea (subtotal / cantidad).
SET @sql := IF(@ready = 1,
  'UPDATE product_orders po
      JOIN products p ON p.id_product = po.product_id
      LEFT JOIN product_skus s ON s.id_sku = (
           SELECT s2.id_sku FROM product_skus s2
            WHERE s2.product_id = po.product_id AND s2.legacy_child_id IS NULL AND s2.deleted = 0
            ORDER BY s2.is_default DESC, s2.id_sku LIMIT 1)
      SET po.sku_id = COALESCE(po.sku_id, s.id_sku),
          po.sku_code = COALESCE(po.sku_code, s.code),
          po.product_name = COALESCE(po.product_name, LEFT(p.name, 255)),
          po.unit_price = COALESCE(po.unit_price, IF(po.quantity > 0, ROUND(po.subtotal / po.quantity, 2), po.subtotal))
    WHERE po.sku_id IS NULL OR po.product_name IS NULL OR po.unit_price IS NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
