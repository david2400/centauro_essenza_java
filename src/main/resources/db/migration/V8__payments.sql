-- =============================================================================
-- V8 · Medios de pago y pagos de órdenes (físicos y virtuales)
--
--   payment_types   pasa a ser el catálogo de medios de pago: code (único entre no
--                   borrados), channel (PHYSICAL | VIRTUAL), kind (CASH, CARD_TERMINAL,
--                   BANK_TRANSFER, DIGITAL_WALLET, GATEWAY, CASH_ON_DELIVERY, OTHER),
--                   provider de pasarela (SANDBOX, WOMPI, MERCADO_PAGO), comisión
--                   (porcentaje + fija), montos mínimo/máximo, referencia obligatoria,
--                   admite cambio (efectivo), instrucciones, activo y orden.
--                   Se precargan los medios habituales (las pasarelas reales inactivas).
--   payments        pagos de una orden: monto, comisión, efectivo recibido y cambio,
--                   estado (PENDING → APPROVED / DECLINED / EXPIRED / VOIDED;
--                   APPROVED → VOIDED / REFUNDED), referencia manual (voucher,
--                   comprobante) y de la pasarela, link de pago, reembolsado.
--   payment_events  bitácora de cambios y de webhooks (idempotencia por evento).
--
-- No destructivo e idempotente.
-- =============================================================================

SET @has_types := (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_types');

-- Medios existentes sin código: código derivado del nombre y del id (único).
SET @sql := IF(@has_types = 1,
  'UPDATE payment_types
      SET code = CONCAT(LEFT(TRIM(BOTH ''_'' FROM REGEXP_REPLACE(UPPER(name), ''[^A-Z0-9]+'', ''_'')), 28), ''_'', id_payment_type)
    WHERE code IS NULL OR code = ''''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Medios de pago habituales (solo los que faltan por código) ----------------------
SET @sql := IF(@has_types = 1,
  'INSERT INTO payment_types (code, name, description, instructions, channel, kind, provider, active, position,
                              fee_percent, fee_fixed, requires_reference, allows_change, usr_crea, created_at, deleted)
   SELECT s.code, s.name, s.description, s.instructions, s.channel, s.kind, s.provider, s.active, s.position,
          0, 0, s.requires_reference, s.allows_change, 1, NOW(6), 0
     FROM (
       SELECT ''CASH'' code, ''Efectivo'' name, ''Pago en efectivo en tienda o punto de venta'' description,
              NULL instructions, ''PHYSICAL'' channel, ''CASH'' kind, NULL provider, 1 active, 10 position,
              0 requires_reference, 1 allows_change
       UNION ALL SELECT ''CARD_TERMINAL'', ''Datáfono'', ''Tarjeta débito o crédito en datáfono'',
              ''Registra el número de aprobación del voucher.'', ''PHYSICAL'', ''CARD_TERMINAL'', NULL, 1, 20, 1, 0
       UNION ALL SELECT ''BANK_TRANSFER'', ''Transferencia bancaria'', ''Transferencia o consignación a la cuenta de la tienda'',
              ''Registra el número del comprobante y confírmalo al verlo en el banco.'', ''VIRTUAL'', ''BANK_TRANSFER'', NULL, 1, 30, 1, 0
       UNION ALL SELECT ''DIGITAL_WALLET'', ''Nequi / Daviplata'', ''Billetera digital confirmada manualmente'',
              ''Registra el número de la transacción.'', ''VIRTUAL'', ''DIGITAL_WALLET'', NULL, 1, 40, 1, 0
       UNION ALL SELECT ''CASH_ON_DELIVERY'', ''Contraentrega'', ''El cliente paga al recibir el pedido'',
              NULL, ''PHYSICAL'', ''CASH_ON_DELIVERY'', NULL, 1, 50, 0, 1
       UNION ALL SELECT ''SANDBOX'', ''Pasarela de pruebas'', ''Simulador interno de pagos en línea (solo pruebas)'',
              NULL, ''VIRTUAL'', ''GATEWAY'', ''SANDBOX'', 1, 90, 0, 0
       UNION ALL SELECT ''WOMPI'', ''Wompi'', ''Tarjeta, PSE, Nequi y Bancolombia en línea'',
              NULL, ''VIRTUAL'', ''GATEWAY'', ''WOMPI'', 0, 60, 0, 0
       UNION ALL SELECT ''MERCADO_PAGO'', ''Mercado Pago'', ''Tarjeta, PSE y efectivo vía Mercado Pago'',
              NULL, ''VIRTUAL'', ''GATEWAY'', ''MERCADO_PAGO'', 0, 70, 0, 0
     ) s
    WHERE NOT EXISTS (SELECT 1 FROM payment_types t WHERE t.code = s.code AND t.deleted = 0)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
