-- Despachos por subpedido: fecha límite de despacho de cada orden.
--   orders.ship_by   día hábil en que la orden debe salir (pago + días de alistamiento de la
--                    oferta en subpedidos de vendedores externos, o essenza.orders.handling-days
--                    en los propios). La fija el servicio al pagarse la orden.
--
-- No destructivo e idempotente: solo agrega una columna NULL y un índice si orders existe.
-- No calcula fechas de órdenes anteriores (eso se hace desde el tablero con una acción explícita).

SET @has_orders := (SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders');
SET @missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'ship_by');
SET @sql := IF(@has_orders = 1 AND @missing = 1,
  'ALTER TABLE orders ADD COLUMN ship_by DATE NULL, ADD KEY ix_orders_state_ship_by (state, ship_by)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
