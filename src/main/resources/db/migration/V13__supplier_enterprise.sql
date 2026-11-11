-- =============================================================================
-- V13 · Proveedores empresariales
--
--   suppliers           código, datos fiscales (tipo y número de documento, DV, tipo de
--                       persona, razón social y nombre comercial, régimen, gran
--                       contribuyente, autorretenedor), ubicación del servicio parametros,
--                       estado (activo, bloqueado con motivo, inactivo), condiciones
--                       comerciales (plazo, cupo, moneda, entrega, pedido mínimo,
--                       descuento), cuenta bancaria y notas. Teléfono, correo y dirección
--                       dejan de ser obligatorios en la base (los valida el dominio).
--   supplier_contacts   personas de contacto por área; una principal.
--   supplier_products   SKU que surte cada proveedor: código del proveedor, costo,
--                       entrega, cantidad mínima y proveedor preferido del SKU.
--   Los proveedores existentes reciben el código PROV_<id> y quedan activos.
--
-- No destructivo e idempotente.
-- =============================================================================

-- 1. suppliers (en una base vacía se crea con las columnas que dejó Hibernate) --------

UPDATE suppliers SET code = CONCAT('PROV_', id_supplier) WHERE code IS NULL;

UPDATE suppliers SET email = NULL WHERE email = '';

UPDATE suppliers SET phone = NULL WHERE phone = '';

UPDATE suppliers SET address = NULL WHERE address = '';
