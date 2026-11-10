-- Consignación: la tienda cobra y liquida al vendedor; almacenamiento por unidad y día.
--   mp_seller_ledger.entry_type  + CONSIGNMENT_SALE (abono: lo cobrado por la tienda para el vendedor),
--                                  CONSIGNMENT_REVERSAL (cancelación o devolución de esa venta),
--                                  SETTLEMENT (liquidación pagada al vendedor) y STORAGE_FEE (almacenamiento).
--   mp_plans.storage_fee_unit_day tarifa de almacenamiento por unidad y día (0 = no se cobra).
--
-- No destructivo e idempotente. La CHECK de tipos se reemplaza por una que acepta los anteriores y los nuevos.

SET @has_ck := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mp_seller_ledger' AND CONSTRAINT_NAME = 'ck_mp_seller_ledger_type');
SET @sql := IF(@has_ck = 1, CONCAT('ALTER TABLE mp_seller_ledger DROP ', IF(VERSION() LIKE '%MariaDB%', 'CONSTRAINT', 'CHECK'), ' ck_mp_seller_ledger_type'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE mp_seller_ledger ADD CONSTRAINT ck_mp_seller_ledger_type CHECK (entry_type IN ('COMMISSION', 'COMMISSION_REVERSAL',
    'MONTHLY_FEE', 'PAYMENT', 'ADJUSTMENT', 'CONSIGNMENT_SALE', 'CONSIGNMENT_REVERSAL', 'SETTLEMENT', 'STORAGE_FEE'));

SET @missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mp_plans' AND COLUMN_NAME = 'storage_fee_unit_day');
SET @sql := IF(@missing = 1, 'ALTER TABLE mp_plans ADD COLUMN storage_fee_unit_day DECIMAL(19,4) NOT NULL DEFAULT 0', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
