-- =============================================================================
-- V11 · Puntos de venta (tiendas) y configuración de cajas
--
--   stores                  sede/tienda: código, dirección y contacto, bodega de la que
--                           salen las ventas de mostrador, horario y textos del ticket.
--   cash_registers          store_id (cada caja pertenece a una tienda) y reglas:
--                           base de apertura (libre, sugerida o fija), tope de efectivo,
--                           arqueo ciego, tolerancia de diferencia, descuento máximo,
--                           factura automática, ancho del ticket y equipo.
--   cash_register_methods   medios de pago permitidos por caja (sin filas = todos).
--   Se crea "Tienda principal" con la bodega principal y se le asignan las cajas existentes.
--
-- No destructivo e idempotente.
-- =============================================================================

SET @has_registers := (SELECT COUNT(*) FROM information_schema.TABLES
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cash_registers');

SET @has_warehouses := (SELECT COUNT(*) FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouses');

-- Tienda principal con la bodega principal (o la primera).
SET @sql := IF(@has_warehouses = 1,
  'INSERT INTO stores (code, name, warehouse_id, ticket_footer, active, usr_crea, created_at, deleted)
   SELECT ''TIENDA_1'', ''Tienda principal'',
          (SELECT w.id_warehouse FROM warehouses w WHERE w.deleted = 0 ORDER BY w.is_main DESC, w.id_warehouse LIMIT 1),
          ''¡Gracias por tu compra!'', 1, 1, NOW(6), 0 FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM stores WHERE deleted = 0)',
  'INSERT INTO stores (code, name, ticket_footer, active, usr_crea, created_at, deleted)
   SELECT ''TIENDA_1'', ''Tienda principal'', ''¡Gracias por tu compra!'', 1, 1, NOW(6), 0 FROM DUAL
    WHERE NOT EXISTS (SELECT 1 FROM stores WHERE deleted = 0)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_registers = 1,
  'UPDATE cash_registers SET store_id = (SELECT s.id_store FROM stores s WHERE s.deleted = 0 ORDER BY s.id_store LIMIT 1)
    WHERE store_id IS NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
