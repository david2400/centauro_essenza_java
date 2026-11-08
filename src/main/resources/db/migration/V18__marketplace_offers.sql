-- =============================================================================
-- V18 · Marketplace: ofertas de vendedores y moderación (fase 2)
--
--   mp_offers         condiciones de un vendedor externo para vender un SKU del catálogo
--                     (precio, moneda, cantidad declarada, alistamiento, condición). El
--                     producto y el SKU siguen siendo los del catálogo (product_skus).
--                     A lo sumo una oferta abierta (no cerrada) por vendedor y SKU.
--   mp_offer_events   auditoría de moderación y cambios (actor, fecha, motivo, antes → después).
--
-- Solo crea tablas nuevas (CREATE TABLE IF NOT EXISTS): no toca datos existentes.
-- =============================================================================

CREATE TABLE IF NOT EXISTS mp_offers (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    company_id          BIGINT        NOT NULL,
    seller_id           BIGINT        NOT NULL,
    sku_id              BIGINT        NOT NULL,
    product_id          BIGINT        NULL,
    -- Copia para buscar y listar; la ficha muestra siempre los datos vigentes del catálogo.
    sku_code            VARCHAR(100)  NULL,
    product_name        VARCHAR(300)  NULL,
    currency            CHAR(3)       NOT NULL,
    price               DECIMAL(19,4) NOT NULL,
    available_quantity  INT           NOT NULL DEFAULT 0,
    handling_days       INT           NOT NULL DEFAULT 1,
    item_condition      VARCHAR(12)   NOT NULL DEFAULT 'NEW',
    fulfillment         VARCHAR(10)   NOT NULL DEFAULT 'SELLER',
    seller_sku          VARCHAR(60)   NULL,
    status              VARCHAR(16)   NOT NULL,
    status_reason       VARCHAR(1000) NULL,
    status_changed_at   DATETIME(6)   NULL,
    published_at        DATETIME(6)   NULL,
    lock_version        INT           NOT NULL DEFAULT 0,
    created_at          DATETIME(6)   NOT NULL,
    created_by          VARCHAR(64)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    updated_by          VARCHAR(64)   NOT NULL,
    open_key            VARCHAR(41) GENERATED ALWAYS AS (CASE WHEN status <> 'CLOSED' THEN CONCAT(seller_id, ':', sku_id) END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_offers_open (open_key),
    KEY ix_mp_offers_status (company_id, status),
    KEY ix_mp_offers_seller (company_id, seller_id),
    KEY ix_mp_offers_sku (company_id, sku_id),
    CONSTRAINT fk_mp_offers_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id),
    CONSTRAINT fk_mp_offers_sku FOREIGN KEY (sku_id) REFERENCES product_skus (id_sku),
    CONSTRAINT ck_mp_offers_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'ACTIVE', 'REJECTED', 'PAUSED', 'CLOSED')),
    CONSTRAINT ck_mp_offers_condition CHECK (item_condition IN ('NEW', 'USED', 'REFURBISHED')),
    CONSTRAINT ck_mp_offers_fulfillment CHECK (fulfillment IN ('SELLER', 'PLATFORM')),
    CONSTRAINT ck_mp_offers_values CHECK (price > 0 AND available_quantity >= 0 AND handling_days >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_offer_events (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    company_id   BIGINT        NOT NULL,
    offer_id     BIGINT        NOT NULL,
    event_type   VARCHAR(30)   NOT NULL,
    from_status  VARCHAR(16)   NULL,
    to_status    VARCHAR(16)   NULL,
    reason       VARCHAR(1000) NULL,
    detail       VARCHAR(1000) NULL,
    actor_id     VARCHAR(64)   NOT NULL,
    actor_name   VARCHAR(160)  NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_mp_offer_events_offer (offer_id, created_at),
    CONSTRAINT fk_mp_offer_events_offer FOREIGN KEY (offer_id) REFERENCES mp_offers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
