-- Dispersión automática de los lotes de pago a vendedores (Wompi Pagos a terceros).
--   mp_banks          + wompi_bank_id: id (UUID) de la entidad en Wompi (GET /banks).
--   mp_payout_items   + entidad del catálogo y resultado en el proveedor (referencia, id, estado, detalle).
--   mp_payout_batches + envío al proveedor (estado, id del lote en el proveedor, llave de idempotencia).
-- No destructivo e idempotente.

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mp_banks'
                  AND COLUMN_NAME = 'wompi_bank_id') = 0,
  'ALTER TABLE mp_banks ADD COLUMN wompi_bank_id VARCHAR(40) NULL AFTER ach_code', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mp_payout_items'
                  AND COLUMN_NAME = 'provider_status') = 0,
  'ALTER TABLE mp_payout_items ADD COLUMN bank_id BIGINT NULL AFTER bank_code,
     ADD COLUMN provider_reference VARCHAR(40) NULL, ADD COLUMN provider_tx_id VARCHAR(64) NULL,
     ADD COLUMN provider_status VARCHAR(20) NULL, ADD COLUMN provider_detail VARCHAR(300) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mp_payout_batches'
                  AND COLUMN_NAME = 'dispatch_status') = 0,
  'ALTER TABLE mp_payout_batches ADD COLUMN dispatch_provider VARCHAR(20) NULL, ADD COLUMN dispatch_status VARCHAR(20) NULL,
     ADD COLUMN provider_batch_id VARCHAR(64) NULL, ADD COLUMN idempotency_key VARCHAR(64) NULL,
     ADD COLUMN dispatch_detail VARCHAR(500) NULL, ADD COLUMN dispatched_at DATETIME(6) NULL, ADD COLUMN dispatched_by VARCHAR(64) NULL,
     ADD UNIQUE KEY uk_mp_payout_batches_provider (dispatch_provider, provider_batch_id),
     ADD CONSTRAINT ck_mp_payout_batches_dispatch CHECK (dispatch_status IS NULL OR dispatch_status IN (''SENT'', ''FAILED'', ''DONE''))',
  'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
