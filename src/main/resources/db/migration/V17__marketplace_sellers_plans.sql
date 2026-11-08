-- =============================================================================
-- V17 · Marketplace: vendedores y planes comerciales (fase 1)
--
--   mp_sellers             vendedores por empresa (propio o externos), ficha comercial,
--                          responsable, estado con motivo y versión para concurrencia.
--   mp_seller_contacts     contactos del vendedor.
--   mp_seller_documents    METADATOS de los documentos de soporte (el archivo vive en un
--                          almacenamiento privado, nunca en una ruta pública) y su revisión.
--   mp_seller_events       auditoría: actor, fecha, acción, estados y motivo.
--   mp_plans               versiones de planes comerciales (comisión, mensualidad o mixto).
--   mp_plan_events         auditoría de planes.
--   mp_plan_assignments    vigencias de planes por vendedor, con excepciones autorizadas y
--                          copia de las condiciones acordadas (las vigencias pasadas no
--                          cambian aunque cambie el plan).
--
-- Solo crea tablas nuevas (CREATE TABLE IF NOT EXISTS): no toca datos existentes.
-- Montos DECIMAL(19,4) con moneda ISO explícita; porcentajes DECIMAL(7,4).
-- =============================================================================

CREATE TABLE IF NOT EXISTS mp_sellers (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    company_id            BIGINT       NOT NULL,
    code                  VARCHAR(30)  NULL,
    kind                  VARCHAR(10)  NOT NULL,
    legal_name            VARCHAR(200) NOT NULL,
    trade_name            VARCHAR(200) NULL,
    doc_type              VARCHAR(5)   NOT NULL,
    doc_number            VARCHAR(30)  NOT NULL,
    email                 VARCHAR(160) NULL,
    phone                 VARCHAR(40)  NULL,
    website               VARCHAR(200) NULL,
    address               VARCHAR(250) NULL,
    city                  VARCHAR(120) NULL,
    description           TEXT         NULL,
    account_manager_id    VARCHAR(64)  NULL,
    account_manager_name  VARCHAR(160) NULL,
    status                VARCHAR(20)  NOT NULL,
    status_reason         VARCHAR(1000) NULL,
    status_changed_at     DATETIME(6)  NULL,
    approved_at           DATETIME(6)  NULL,
    approved_by           VARCHAR(64)  NULL,
    lock_version          INT          NOT NULL DEFAULT 0,
    created_at            DATETIME(6)  NOT NULL,
    created_by            VARCHAR(64)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    updated_by            VARCHAR(64)  NOT NULL,
    -- Un solo vendedor propio por empresa.
    own_company_id        BIGINT GENERATED ALWAYS AS (CASE WHEN kind = 'OWN' THEN company_id END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_sellers_code (company_id, code),
    UNIQUE KEY uk_mp_sellers_doc (company_id, doc_type, doc_number),
    UNIQUE KEY uk_mp_sellers_own (own_company_id),
    KEY ix_mp_sellers_status (company_id, status),
    KEY ix_mp_sellers_name (company_id, legal_name),
    CONSTRAINT ck_mp_sellers_kind CHECK (kind IN ('OWN', 'EXTERNAL')),
    CONSTRAINT ck_mp_sellers_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_mp_sellers_doc_type CHECK (doc_type IN ('NIT', 'CC', 'CE', 'PP'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_seller_contacts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id   BIGINT       NOT NULL,
    position    INT          NOT NULL,
    name        VARCHAR(160) NOT NULL,
    role        VARCHAR(80)  NULL,
    email       VARCHAR(160) NULL,
    phone       VARCHAR(40)  NULL,
    is_primary  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_mp_seller_contacts_seller (seller_id, position),
    CONSTRAINT fk_mp_seller_contacts_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_seller_documents (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    company_id     BIGINT       NOT NULL,
    seller_id      BIGINT       NOT NULL,
    kind           VARCHAR(30)  NOT NULL,
    file_name      VARCHAR(200) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    sha256         CHAR(64)     NOT NULL,
    storage_key    VARCHAR(100) NOT NULL,
    review_status  VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    review_note    VARCHAR(1000) NULL,
    reviewed_at    DATETIME(6)  NULL,
    reviewed_by    VARCHAR(64)  NULL,
    -- Al cargar otro documento del mismo tipo el anterior queda reemplazado (se conserva).
    superseded_at  DATETIME(6)  NULL,
    uploaded_at    DATETIME(6)  NOT NULL,
    uploaded_by    VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_seller_documents_key (storage_key),
    KEY ix_mp_seller_documents_seller (seller_id, kind),
    CONSTRAINT fk_mp_seller_documents_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id),
    CONSTRAINT ck_mp_seller_documents_review CHECK (review_status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_seller_events (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    company_id   BIGINT        NOT NULL,
    seller_id    BIGINT        NOT NULL,
    event_type   VARCHAR(30)   NOT NULL,
    from_status  VARCHAR(20)   NULL,
    to_status    VARCHAR(20)   NULL,
    reason       VARCHAR(1000) NULL,
    detail       VARCHAR(1000) NULL,
    actor_id     VARCHAR(64)   NOT NULL,
    actor_name   VARCHAR(160)  NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_mp_seller_events_seller (seller_id, created_at),
    CONSTRAINT fk_mp_seller_events_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_plans (
    id                             BIGINT        NOT NULL AUTO_INCREMENT,
    company_id                     BIGINT        NOT NULL,
    code                           VARCHAR(30)   NOT NULL,
    version                        INT           NOT NULL,
    name                           VARCHAR(120)  NOT NULL,
    description                    VARCHAR(1000) NULL,
    status                         VARCHAR(12)   NOT NULL,
    modality                       VARCHAR(12)   NOT NULL,
    currency                       CHAR(3)       NOT NULL,
    monthly_fee                    DECIMAL(19,4) NOT NULL DEFAULT 0,
    commission_percent             DECIMAL(7,4)  NOT NULL DEFAULT 0,
    fixed_fee_per_sale             DECIMAL(19,4) NOT NULL DEFAULT 0,
    commission_base                VARCHAR(30)   NOT NULL,
    include_shipping               TINYINT(1)    NOT NULL DEFAULT 0,
    include_taxes                  TINYINT(1)    NOT NULL DEFAULT 0,
    accrual_event                  VARCHAR(30)   NOT NULL,
    return_window_days             INT           NOT NULL DEFAULT 0,
    refund_policy                  VARCHAR(30)   NOT NULL,
    fee_billing                    VARCHAR(12)   NOT NULL,
    grace_days                     INT           NOT NULL DEFAULT 0,
    late_interest_monthly_percent  DECIMAL(7,4)  NOT NULL DEFAULT 0,
    valid_from                     DATE          NOT NULL,
    valid_to                       DATE          NULL,
    published_at                   DATETIME(6)   NULL,
    published_by                   VARCHAR(64)   NULL,
    lock_version                   INT           NOT NULL DEFAULT 0,
    created_at                     DATETIME(6)   NOT NULL,
    created_by                     VARCHAR(64)   NOT NULL,
    updated_at                     DATETIME(6)   NOT NULL,
    updated_by                     VARCHAR(64)   NOT NULL,
    -- A lo sumo un borrador por código.
    draft_code                     VARCHAR(30) GENERATED ALWAYS AS (CASE WHEN status = 'DRAFT' THEN code END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_plans_version (company_id, code, version),
    UNIQUE KEY uk_mp_plans_draft (company_id, draft_code),
    KEY ix_mp_plans_status (company_id, status),
    CONSTRAINT ck_mp_plans_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'RETIRED')),
    CONSTRAINT ck_mp_plans_modality CHECK (modality IN ('COMMISSION', 'SUBSCRIPTION', 'MIXED')),
    CONSTRAINT ck_mp_plans_amounts CHECK (monthly_fee >= 0 AND fixed_fee_per_sale >= 0
        AND commission_percent BETWEEN 0 AND 100 AND late_interest_monthly_percent BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_plan_events (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    company_id  BIGINT        NOT NULL,
    plan_id     BIGINT        NOT NULL,
    event_type  VARCHAR(30)   NOT NULL,
    detail      VARCHAR(1000) NULL,
    actor_id    VARCHAR(64)   NOT NULL,
    actor_name  VARCHAR(160)  NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_mp_plan_events_plan (plan_id, created_at),
    CONSTRAINT fk_mp_plan_events_plan FOREIGN KEY (plan_id) REFERENCES mp_plans (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_plan_assignments (
    id                           BIGINT        NOT NULL AUTO_INCREMENT,
    company_id                   BIGINT        NOT NULL,
    seller_id                    BIGINT        NOT NULL,
    plan_id                      BIGINT        NOT NULL,
    starts_on                    DATE          NOT NULL,
    ends_on                      DATE          NULL,
    override_commission_percent  DECIMAL(7,4)  NULL,
    override_monthly_fee         DECIMAL(19,4) NULL,
    override_reason              VARCHAR(1000) NULL,
    override_approved_by         VARCHAR(64)   NULL,
    -- Condiciones efectivas acordadas (plan + excepciones), en JSON, inmutables.
    agreed_terms                 TEXT          NOT NULL,
    notes                        VARCHAR(1000) NULL,
    cancelled_at                 DATETIME(6)   NULL,
    cancelled_by                 VARCHAR(64)   NULL,
    cancel_reason                VARCHAR(1000) NULL,
    created_at                   DATETIME(6)   NOT NULL,
    created_by                   VARCHAR(64)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_mp_plan_assignments_seller (seller_id, starts_on),
    KEY ix_mp_plan_assignments_plan (plan_id),
    CONSTRAINT fk_mp_plan_assignments_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id),
    CONSTRAINT fk_mp_plan_assignments_plan FOREIGN KEY (plan_id) REFERENCES mp_plans (id),
    CONSTRAINT ck_mp_plan_assignments_dates CHECK (ends_on IS NULL OR ends_on >= starts_on)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
