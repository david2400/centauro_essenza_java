-- =============================================================================
-- V16 · Órdenes empresariales
--
--   orders            número consecutivo (ORD-000123), cliente y datos de facturación
--                     congelados, entrega (envío con dirección y geo, o retiro en
--                     tienda), transportadora, guía, costo de envío, fecha prometida,
--                     origen (tienda, bodega, vendedor, prioridad), descuento global o
--                     cupón, notas internas y para el cliente, y fechas de pago,
--                     despacho, entrega y cancelación.
--   product_orders    order_discount (parte del descuento global de la orden) y
--                     picked_quantity (unidades preparadas).
--   order_events      línea de tiempo auditable de la orden.
--   order_numbering   prefijo, relleno y siguiente número de las órdenes.
--   Las órdenes existentes reciben ORD-<id con 6 dígitos> y un evento de creación.
--
-- No destructivo e idempotente.
-- =============================================================================

SET @has_orders := (SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders');

SET @has_lines := (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_orders');

-- Número para las órdenes existentes y único.
SET @sql := IF(@has_orders = 1,
  'UPDATE orders SET order_number = CONCAT(''ORD-'', LPAD(id_order, 6, ''0'')) WHERE order_number IS NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Fecha de pago aproximada de las órdenes ya pagadas (para los reportes).
SET @sql := IF(@has_orders = 1,
  'UPDATE orders SET paid_at = COALESCE(updated_at, created_at)
    WHERE paid_at IS NULL AND state IN (''PAID'', ''PROCESSING'', ''SHIPPED'', ''DELIVERED'')', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Bodega y tienda de las ventas de caja.
SET @has_cash := (SELECT COUNT(DISTINCT TABLE_NAME) = 2 FROM information_schema.TABLES
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('cash_sessions', 'cash_registers'));

SET @sql := IF(@has_orders = 1 AND @has_cash = 1,
  'UPDATE orders o
     JOIN cash_sessions s ON s.id_cash_session = o.cash_session_id
     JOIN cash_registers r ON r.id_cash_register = s.cash_register_id
      SET o.store_id = COALESCE(o.store_id, r.store_id),
          o.delivery_method = ''PICKUP''
    WHERE o.store_id IS NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Las órdenes ya despachadas o entregadas quedan preparadas por completo.
SET @sql := IF(@has_orders = 1 AND @has_lines = 1,
  'UPDATE product_orders l JOIN orders o ON o.id_order = l.order_id
      SET l.picked_quantity = l.quantity
    WHERE o.state IN (''SHIPPED'', ''DELIVERED'') AND l.picked_quantity = 0', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_orders = 1,
  'INSERT INTO order_events (order_id, event_type, to_state, message, created_at)
   SELECT o.id_order, ''CREATED'', ''PENDING'', NULL, COALESCE(o.created_at, NOW(6))
     FROM orders o
    WHERE NOT EXISTS (SELECT 1 FROM order_events e WHERE e.order_id = o.id_order)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @next := 1;

SET @sql := IF(@has_orders = 1, 'SET @next := (SELECT COALESCE(MAX(id_order), 0) + 1 FROM orders)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO order_numbering (id, prefix, padding, next_number) VALUES (1, 'ORD', 6, @next);
