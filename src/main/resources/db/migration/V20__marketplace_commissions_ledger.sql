-- =============================================================================
-- V20 · Marketplace: comisiones y estado de cuenta del vendedor (fase 4)
--
--   mp_commissions             una por subpedido de vendedor externo pagado: plan vigente, base,
--                              porcentaje, cargo fijo, total, lo ya reversado y estado
--                              (PENDING → ACCRUED | VOIDED; REVIEW si no se pudo calcular).
--   mp_commission_adjustments  reversos por cancelación o devolución (una vez por origen).
--   mp_seller_ledger           estado de cuenta: cargos (+) y abonos (−) del vendedor con la
--                              plataforma. Cada asiento tiene una clave idempotente única.
--
-- Solo crea tablas nuevas. Sin FK a orders (puede crearla Hibernate después de Flyway).
-- =============================================================================

CREATE TABLE IF NOT EXISTS mp_commissions (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    company_id         BIGINT        NOT NULL,
    seller_id          BIGINT        NOT NULL,
    order_id           BIGINT        NOT NULL,
    purchase_id        BIGINT        NULL,
    order_number       VARCHAR(30)   NULL,
    assignment_id      BIGINT        NULL,
    plan_code          VARCHAR(40)   NULL,
    currency           CHAR(3)       NOT NULL,
    items_subtotal     DECIMAL(19,4) NOT NULL,
    discount           DECIMAL(19,4) NOT NULL,
    shipping           DECIMAL(19,4) NOT NULL,
    taxes              DECIMAL(19,4) NOT NULL,
    base               DECIMAL(19,4) NOT NULL,
    percent            DECIMAL(7,4)  NOT NULL,
    percent_part       DECIMAL(19,4) NOT NULL,
    fixed_part         DECIMAL(19,4) NOT NULL,
    amount             DECIMAL(19,4) NOT NULL,
    reversed_amount    DECIMAL(19,4) NOT NULL DEFAULT 0,
    accrual_event      VARCHAR(24)   NULL,
    return_window_days INT           NOT NULL DEFAULT 0,
    paid_on            DATE          NOT NULL,
    delivered_on       DATE          NULL,
    accrue_on          DATE          NULL,
    status             VARCHAR(10)   NOT NULL,
    review_reason      VARCHAR(300)  NULL,
    accrued_at         DATETIME(6)   NULL,
    voided_at          DATETIME(6)   NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_commissions_order (order_id),
    KEY ix_mp_commissions_seller (company_id, seller_id, status),
    KEY ix_mp_commissions_due (status, accrue_on),
    CONSTRAINT fk_mp_commissions_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id),
    CONSTRAINT fk_mp_commissions_assignment FOREIGN KEY (assignment_id) REFERENCES mp_plan_assignments (id),
    CONSTRAINT ck_mp_commissions_status CHECK (status IN ('PENDING', 'ACCRUED', 'VOIDED', 'REVIEW')),
    CONSTRAINT ck_mp_commissions_amounts CHECK (amount >= 0 AND reversed_amount >= 0 AND reversed_amount <= amount)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_commission_adjustments (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    company_id     BIGINT        NOT NULL,
    commission_id  BIGINT        NOT NULL,
    kind           VARCHAR(10)   NOT NULL,
    source_key     VARCHAR(80)   NOT NULL,
    refunded_base  DECIMAL(19,4) NOT NULL,
    amount         DECIMAL(19,4) NOT NULL,
    posted         TINYINT(1)    NOT NULL DEFAULT 0,
    note           VARCHAR(500)  NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_commission_adjustments_source (source_key),
    KEY ix_mp_commission_adjustments_commission (commission_id),
    CONSTRAINT fk_mp_commission_adjustments_commission FOREIGN KEY (commission_id) REFERENCES mp_commissions (id),
    CONSTRAINT ck_mp_commission_adjustments_kind CHECK (kind IN ('CANCEL', 'REFUND')),
    CONSTRAINT ck_mp_commission_adjustments_amount CHECK (amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_seller_ledger (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    company_id           BIGINT        NOT NULL,
    seller_id            BIGINT        NOT NULL,
    entry_type           VARCHAR(20)   NOT NULL,
    amount               DECIMAL(19,4) NOT NULL,
    currency             CHAR(3)       NOT NULL,
    entry_date           DATE          NOT NULL,
    due_on               DATE          NULL,
    interest_monthly_pct DECIMAL(7,4)  NOT NULL DEFAULT 0,
    reference            VARCHAR(80)   NULL,
    commission_id        BIGINT        NULL,
    fee_cycle            CHAR(7)       NULL,
    idempotency_key      VARCHAR(80)   NOT NULL,
    note                 VARCHAR(500)  NULL,
    created_by           VARCHAR(64)   NOT NULL,
    created_by_name      VARCHAR(120)  NULL,
    created_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_seller_ledger_key (company_id, idempotency_key),
    KEY ix_mp_seller_ledger_seller (company_id, seller_id, entry_date),
    CONSTRAINT fk_mp_seller_ledger_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id),
    CONSTRAINT fk_mp_seller_ledger_commission FOREIGN KEY (commission_id) REFERENCES mp_commissions (id),
    CONSTRAINT ck_mp_seller_ledger_type CHECK (entry_type IN ('COMMISSION', 'COMMISSION_REVERSAL', 'MONTHLY_FEE', 'PAYMENT', 'ADJUSTMENT')),
    CONSTRAINT ck_mp_seller_ledger_amount CHECK (amount <> 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
