-- =============================================================================
-- V21 · Clientes persistentes (ficha 360)
--
--   customers  cliente de la tienda por empresa, identificado por su documento (tipo + número).
--              Las órdenes conservan su propia foto del cliente; la ficha 360 las relaciona por
--              documento (orders.customer_doc_type / customer_doc_number, ya indexado en V16).
--              marketing_consent / consent_at: autorización de comunicaciones (habeas data).
--
-- Solo crea una tabla nueva: no toca datos existentes. Los clientes de órdenes anteriores se
-- crean desde el panel ("Importar desde órdenes"), no en esta migración.
-- =============================================================================

CREATE TABLE IF NOT EXISTS customers (
    id_customer        BIGINT        NOT NULL AUTO_INCREMENT,
    company_id         BIGINT        NOT NULL,
    kind               VARCHAR(10)   NOT NULL,
    doc_type           VARCHAR(10)   NOT NULL,
    doc_number         VARCHAR(30)   NOT NULL,
    name               VARCHAR(200)  NOT NULL,
    email              VARCHAR(160)  NULL,
    phone              VARCHAR(40)   NULL,
    billing_address    VARCHAR(200)  NULL,
    billing_city       VARCHAR(120)  NULL,
    store_user_id      BIGINT        NULL,
    marketing_consent  TINYINT(1)    NOT NULL DEFAULT 0,
    consent_at         DATETIME(6)   NULL,
    notes              VARCHAR(2000) NULL,
    source             VARCHAR(12)   NOT NULL,
    lock_version       INT           NOT NULL DEFAULT 0,
    created_at         DATETIME(6)   NOT NULL,
    created_by         VARCHAR(64)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    updated_by         VARCHAR(64)   NOT NULL,
    PRIMARY KEY (id_customer),
    UNIQUE KEY uk_customers_doc (company_id, doc_type, doc_number),
    KEY ix_customers_name (company_id, name),
    KEY ix_customers_email (company_id, email),
    CONSTRAINT ck_customers_kind CHECK (kind IN ('PERSON', 'COMPANY')),
    CONSTRAINT ck_customers_source CHECK (source IN ('PANEL', 'ORDERS'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
