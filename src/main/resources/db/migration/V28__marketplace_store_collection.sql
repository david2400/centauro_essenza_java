-- La tienda cobra los subpedidos de vendedores externos y le liquida a cada vendedor.
--   mp_seller_ledger               + MARKETPLACE_SALE (abono al vendedor por lo que cobró la tienda) y
--                                  MARKETPLACE_REVERSAL (cancelación o reembolso de lo abonado).
--   payment_checkout_groups        un solo link de pago para toda una compra multivendedor.
--   payment_checkout_group_items   los pagos (uno por subpedido) que cubre ese link.
--
-- No destructivo e idempotente.

SET @has_ck := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mp_seller_ledger' AND CONSTRAINT_NAME = 'ck_mp_seller_ledger_type');
SET @sql := IF(@has_ck = 1, CONCAT('ALTER TABLE mp_seller_ledger DROP ', IF(VERSION() LIKE '%MariaDB%', 'CONSTRAINT', 'CHECK'), ' ck_mp_seller_ledger_type'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE mp_seller_ledger ADD CONSTRAINT ck_mp_seller_ledger_type CHECK (entry_type IN ('COMMISSION', 'COMMISSION_REVERSAL',
    'MONTHLY_FEE', 'PAYMENT', 'ADJUSTMENT', 'CONSIGNMENT_SALE', 'CONSIGNMENT_REVERSAL', 'SETTLEMENT', 'STORAGE_FEE',
    'DISPUTE_CHARGE', 'MARKETPLACE_SALE', 'MARKETPLACE_REVERSAL'));

CREATE TABLE IF NOT EXISTS payment_checkout_groups (
    reference    VARCHAR(40)   NOT NULL,
    company_id   BIGINT        NOT NULL,
    purchase_id  BIGINT        NOT NULL,
    provider     VARCHAR(30)   NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    created_by   VARCHAR(64)   NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (reference),
    KEY ix_payment_checkout_groups_purchase (company_id, purchase_id),
    CONSTRAINT ck_payment_checkout_groups_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS payment_checkout_group_items (
    reference   VARCHAR(40) NOT NULL,
    payment_id  BIGINT      NOT NULL,
    PRIMARY KEY (reference, payment_id),
    UNIQUE KEY uk_payment_checkout_group_items_payment (payment_id),
    CONSTRAINT fk_payment_checkout_group_items_group FOREIGN KEY (reference) REFERENCES payment_checkout_groups (reference)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

SET @ok := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments'
              AND COLUMN_NAME = 'id_payment')
         * (SELECT COUNT(*) = 0 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'payment_checkout_group_items' AND CONSTRAINT_NAME = 'fk_payment_checkout_group_items_payment');
SET @sql := IF(@ok = 1, 'ALTER TABLE payment_checkout_group_items ADD CONSTRAINT fk_payment_checkout_group_items_payment
                          FOREIGN KEY (payment_id) REFERENCES payments (id_payment)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
