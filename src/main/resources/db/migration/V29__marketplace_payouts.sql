-- Pagos a vendedores por lote (liquidaciones de lo que la tienda cobró por ellos).
--   mp_seller_bank_accounts  cuenta para transferirle a cada vendedor (una por vendedor).
--   mp_payout_batches        lote LIQ-000001: abierto, cerrado o anulado.
--   mp_payout_items          un pago por vendedor, con la cuenta copiada al crear el lote.
--                            Pagado = asiento SETTLEMENT en su estado de cuenta (clave PAYOUT:{item}).
--
-- Un vendedor solo puede tener un pago pendiente a la vez (columna generada única).
-- No destructivo e idempotente: solo crea tablas.

CREATE TABLE IF NOT EXISTS mp_seller_bank_accounts (
    seller_id          BIGINT        NOT NULL,
    company_id         BIGINT        NOT NULL,
    bank_name          VARCHAR(80)   NOT NULL,
    account_type       VARCHAR(20)   NOT NULL,
    account_number     VARCHAR(30)   NOT NULL,
    holder_name        VARCHAR(160)  NOT NULL,
    holder_doc_type    VARCHAR(10)   NOT NULL,
    holder_doc_number  VARCHAR(30)   NOT NULL,
    updated_by         VARCHAR(64)   NOT NULL,
    updated_by_name    VARCHAR(120)  NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (seller_id),
    KEY ix_mp_seller_bank_accounts_company (company_id),
    CONSTRAINT fk_mp_seller_bank_accounts_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id),
    CONSTRAINT ck_mp_seller_bank_accounts_type CHECK (account_type IN ('SAVINGS', 'CHECKING', 'DIGITAL_WALLET'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_payout_batches (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    company_id       BIGINT         NOT NULL,
    batch_number     VARCHAR(20)    NULL,
    status           VARCHAR(20)    NOT NULL,
    hold_days        INT            NOT NULL,
    note             VARCHAR(500)   NULL,
    status_reason    VARCHAR(500)   NULL,
    created_by       VARCHAR(64)    NOT NULL,
    created_by_name  VARCHAR(120)   NULL,
    created_at       DATETIME(6)    NOT NULL,
    closed_at        DATETIME(6)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_payout_batches_number (company_id, batch_number),
    KEY ix_mp_payout_batches_status (company_id, status, created_at),
    CONSTRAINT ck_mp_payout_batches_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_payout_items (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    batch_id           BIGINT         NOT NULL,
    seller_id          BIGINT         NOT NULL,
    seller_name        VARCHAR(200)   NULL,
    seller_doc         VARCHAR(40)    NULL,
    amount             DECIMAL(19,2)  NOT NULL,
    currency           CHAR(3)        NOT NULL DEFAULT 'COP',
    bank_name          VARCHAR(80)    NOT NULL,
    account_type       VARCHAR(20)    NOT NULL,
    account_number     VARCHAR(30)    NOT NULL,
    holder_name        VARCHAR(160)   NOT NULL,
    holder_doc_type    VARCHAR(10)    NOT NULL,
    holder_doc_number  VARCHAR(30)    NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    reference          VARCHAR(80)    NULL,
    paid_on            DATE           NULL,
    note               VARCHAR(500)   NULL,
    actor_id           VARCHAR(64)    NULL,
    actor_name         VARCHAR(120)   NULL,
    updated_at         DATETIME(6)    NOT NULL,
    open_seller        BIGINT AS (IF(status = 'PENDING', seller_id, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_payout_items_open_seller (open_seller),
    KEY ix_mp_payout_items_batch (batch_id, id),
    KEY ix_mp_payout_items_seller (seller_id),
    CONSTRAINT fk_mp_payout_items_batch FOREIGN KEY (batch_id) REFERENCES mp_payout_batches (id),
    CONSTRAINT fk_mp_payout_items_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id),
    CONSTRAINT ck_mp_payout_items_status CHECK (status IN ('PENDING', 'PAID', 'REJECTED')),
    CONSTRAINT ck_mp_payout_items_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
