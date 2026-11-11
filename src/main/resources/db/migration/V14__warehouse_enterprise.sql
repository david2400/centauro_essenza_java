-- =============================================================================
-- V14 · Bodegas empresariales
--
--   warehouses            tipo (ALMACEN, TIENDA, TRANSITO, DEVOLUCIONES, CONSIGNACION),
--                         operación (vende, despacha pedidos web, recibe compras,
--                         prioridad de despacho), responsable (nombre, teléfono, correo),
--                         horario, notas, capacidad (m², unidades, posiciones) y nombre
--                         de la ciudad del catálogo de parametros. Código único entre las
--                         bodegas no borradas (si hoy no hay códigos repetidos).
--   warehouse_locations   ubicaciones internas: código, zona, pasillo, estante, nivel,
--                         tipo (almacenamiento, picking, recepción, despacho, cuarentena)
--                         y capacidad.
--   Las bodegas existentes quedan como ALMACEN con todas las operaciones habilitadas.
--
-- No destructivo e idempotente.
-- =============================================================================

-- 1. warehouses (en una base vacía se crea con las columnas que dejó Hibernate) -------

-- Bodegas sin código (registros antiguos): BOD_<id>.
UPDATE warehouses SET code = CONCAT('BOD_', id_warehouse) WHERE code IS NULL OR TRIM(code) = '';
