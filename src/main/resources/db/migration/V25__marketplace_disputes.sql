-- Reclamos y disputas del comprador contra subpedidos de vendedores externos.
--   mp_disputes        reclamo (DSP-000001), plazo de respuesta del vendedor, respuesta y decisión.
--   mp_dispute_events  historial (apertura, respuesta, decisión, retiro).
--   mp_seller_ledger   + DISPUTE_CHARGE (cargo al vendedor por un reclamo resuelto a favor del comprador).
--
-- No destructivo e idempotente. Un solo reclamo abierto por orden (columna generada única).

CREATE TABLE IF NOT EXISTS mp_disputes (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    company_id         BIGINT        NOT NULL,
    dispute_number     VARCHAR(20)   NULL,
    order_id           BIGINT        NOT NULL,
    purchase_id        BIGINT        NULL,
    seller_id          BIGINT        NOT NULL,
    reason             VARCHAR(20)   NOT NULL,
    description        VARCHAR(1000) NOT NULL,
    claimed_amount     DECIMAL(19,4) NULL,
    status             VARCHAR(20)   NOT NULL,
    respond_by         DATE          NOT NULL,
    seller_response    VARCHAR(1000) NULL,
    responded_at       DATETIME(6)   NULL,
    resolution         VARCHAR(20)   NULL,
    resolution_amount  DECIMAL(19,4) NULL,
    seller_charge      DECIMAL(19,4) NULL,
    resolution_note    VARCHAR(1000) NULL,
    devolution_id      BIGINT        NULL,
    resolved_at        DATETIME(6)   NULL,
    resolved_by        VARCHAR(64)   NULL,
    lock_version       INT           NOT NULL DEFAULT 0,
    created_by         VARCHAR(64)   NOT NULL,
    created_by_name    VARCHAR(120)  NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    open_order         BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('OPEN', 'SELLER_RESPONDED') THEN order_id END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_disputes_number (company_id, dispute_number),
    UNIQUE KEY uk_mp_disputes_open_order (open_order),
    KEY ix_mp_disputes_status (company_id, status, respond_by),
    KEY ix_mp_disputes_seller (company_id, seller_id),
    KEY ix_mp_disputes_order (order_id),
    CONSTRAINT fk_mp_disputes_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id),
    CONSTRAINT ck_mp_disputes_status CHECK (status IN ('OPEN', 'SELLER_RESPONDED', 'RESOLVED_BUYER', 'RESOLVED_SELLER', 'CANCELLED')),
    CONSTRAINT ck_mp_disputes_reason CHECK (reason IN ('NOT_RECEIVED', 'DEFECTIVE', 'NOT_AS_DESCRIBED', 'WRONG_ITEM', 'OTHER')),
    CONSTRAINT ck_mp_disputes_amounts CHECK ((claimed_amount IS NULL OR claimed_amount >= 0)
        AND (resolution_amount IS NULL OR resolution_amount >= 0) AND (seller_charge IS NULL OR seller_charge >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_dispute_events (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    dispute_id   BIGINT        NOT NULL,
    action       VARCHAR(20)   NOT NULL,
    from_status  VARCHAR(20)   NULL,
    to_status    VARCHAR(20)   NOT NULL,
    note         VARCHAR(1000) NULL,
    actor_id     VARCHAR(64)   NOT NULL,
    actor_name   VARCHAR(120)  NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_mp_dispute_events_dispute (dispute_id, id),
    CONSTRAINT fk_mp_dispute_events_dispute FOREIGN KEY (dispute_id) REFERENCES mp_disputes (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

SET @has_ck := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mp_seller_ledger' AND CONSTRAINT_NAME = 'ck_mp_seller_ledger_type');
SET @sql := IF(@has_ck = 1, CONCAT('ALTER TABLE mp_seller_ledger DROP ', IF(VERSION() LIKE '%MariaDB%', 'CONSTRAINT', 'CHECK'), ' ck_mp_seller_ledger_type'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE mp_seller_ledger ADD CONSTRAINT ck_mp_seller_ledger_type CHECK (entry_type IN ('COMMISSION', 'COMMISSION_REVERSAL',
    'MONTHLY_FEE', 'PAYMENT', 'ADJUSTMENT', 'CONSIGNMENT_SALE', 'CONSIGNMENT_REVERSAL', 'SETTLEMENT', 'STORAGE_FEE',
    'DISPUTE_CHARGE'));
