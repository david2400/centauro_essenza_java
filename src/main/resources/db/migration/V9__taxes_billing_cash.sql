-- =============================================================================
-- V9 · Impuestos, facturación electrónica y caja (punto de venta)
--
--   tax_rates          tarifas (IVA 19 %, IVA 5 %, IVA exento 0 %, excluido, INC 8 %);
--                      una por defecto. products.tax_rate_id (NULL = la por defecto).
--   product_orders     impuesto congelado por línea: tarifa, base y valor. Los precios
--                      incluyen IVA: base = total / (1 + tarifa), impuesto = total − base.
--   orders             tax_amount, net_amount (suma de bases), channel (BACKOFFICE | POS |
--                      ONLINE) y cash_session_id.
--   billing_settings   datos del emisor (NIT, razón social, régimen…), ambiente,
--                      proveedor, software y facturación automática al pagar.
--   billing_resolutions numeración autorizada por la DIAN (prefijo, rango, vigencia,
--                      clave técnica) para facturas y notas crédito.
--   invoices / invoice_lines  facturas y notas crédito con CUFE/CUDE, comprador,
--                      totales e impuestos por línea. Una factura vigente por orden.
--   cash_registers / cash_sessions / cash_session_counts / cash_movements  cajas,
--                      turnos (apertura con base, cierre con arqueo por medio de pago)
--                      y entradas/salidas de efectivo. payments.cash_session_id.
--
-- No destructivo e idempotente.
-- =============================================================================

SET @has_orders := (SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders');

SET @has_lines := (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_orders');

SET @has_products := (SELECT COUNT(*) FROM information_schema.TABLES
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products');

SET @ready := (@has_orders = 1 AND @has_lines = 1 AND @has_products = 1);

INSERT INTO tax_rates (code, name, description, kind, rate, is_default, active, position, usr_crea, created_at, deleted)
SELECT s.code, s.name, s.description, s.kind, s.rate, s.is_default, 1, s.position, 1, NOW(6), 0
  FROM (
    SELECT 'IVA_19' code, 'IVA 19 %' name, 'Tarifa general del IVA' description, 'IVA' kind, 19.000 rate, 1 is_default, 10 position
    UNION ALL SELECT 'IVA_5', 'IVA 5 %', 'Tarifa diferencial del IVA', 'IVA', 5.000, 0, 20
    UNION ALL SELECT 'IVA_0', 'IVA exento (0 %)', 'Bienes y servicios exentos: se facturan con IVA 0 %', 'IVA', 0.000, 0, 30
    UNION ALL SELECT 'EXCLUIDO', 'Excluido de IVA', 'No causan IVA', 'EXCLUDED', 0.000, 0, 40
    UNION ALL SELECT 'INC_8', 'Impuesto al consumo 8 %', 'Impuesto nacional al consumo (restaurantes, bares…)', 'INC', 8.000, 0, 50
  ) s
 WHERE NOT EXISTS (SELECT 1 FROM tax_rates t WHERE t.code = s.code AND t.deleted = 0);

-- Líneas existentes: tarifa del producto o la por defecto (los precios incluyen IVA).
SET @sql := IF(@ready = 1,
  'UPDATE product_orders l
     JOIN products p ON p.id_product = l.product_id
     JOIN tax_rates t ON t.id_tax_rate = COALESCE(p.tax_rate_id,
            (SELECT d.id_tax_rate FROM tax_rates d WHERE d.is_default = 1 AND d.deleted = 0 ORDER BY d.id_tax_rate LIMIT 1))
      SET l.tax_rate_id = t.id_tax_rate, l.tax_code = t.code, l.tax_kind = t.kind, l.tax_rate = t.rate,
          l.tax_base = ROUND(l.total / (1 + t.rate / 100), 2),
          l.tax_amount = l.total - ROUND(l.total / (1 + t.rate / 100), 2)
    WHERE l.tax_amount IS NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@ready = 1,
  'UPDATE orders o
      SET o.tax_amount = COALESCE((SELECT SUM(l.tax_amount) FROM product_orders l WHERE l.order_id = o.id_order AND l.deleted = 0), 0),
          o.net_amount = COALESCE((SELECT SUM(COALESCE(l.tax_base, l.total)) FROM product_orders l WHERE l.order_id = o.id_order AND l.deleted = 0), 0)
    WHERE o.tax_amount = 0 AND o.net_amount = 0', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO billing_settings (id_billing_settings, tax_regime, environment, provider, auto_invoice, usr_crea, created_at, deleted)
SELECT 1, 'RESPONSABLE_IVA', 'TEST', 'SIMULATOR', 0, 1, NOW(6), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM billing_settings WHERE id_billing_settings = 1);

-- Numeración del set de pruebas de habilitación de la DIAN (ambiente de pruebas).
INSERT INTO billing_resolutions (kind, prefix, resolution_number, resolution_date, technical_key, from_number, to_number,
                                 next_number, valid_from, valid_to, environment, active, usr_crea, created_at, deleted)
SELECT 'INVOICE', 'SETT', '18760000001', '2019-01-19', 'fc8eac422eba16e22ffd8c6f94b3f40a6e38162c', 1, 5000000, 1,
       '2019-01-19', '2030-01-19', 'TEST', 1, 1, NOW(6), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM billing_resolutions WHERE kind = 'INVOICE' AND deleted = 0);

INSERT INTO billing_resolutions (kind, prefix, resolution_number, from_number, to_number, next_number, environment, active,
                                 usr_crea, created_at, deleted)
SELECT 'CREDIT_NOTE', 'NC', NULL, 1, 99999999, 1, 'TEST', 1, 1, NOW(6), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM billing_resolutions WHERE kind = 'CREDIT_NOTE' AND deleted = 0);

INSERT INTO cash_registers (code, name, location, active, usr_crea, created_at, deleted)
SELECT 'CAJA_1', 'Caja principal', 'Tienda', 1, 1, NOW(6), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM cash_registers WHERE deleted = 0);
