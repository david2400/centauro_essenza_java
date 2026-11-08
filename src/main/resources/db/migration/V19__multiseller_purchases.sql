-- =============================================================================
-- V19 · Compras multivendedor (marketplace, fase 3)
--
--   purchases               compra del comprador: agrupa un subpedido (orden) por vendedor.
--                           Su estado y su total NO se guardan: se derivan de los subpedidos.
--   orders                  purchase_id (compra a la que pertenece) y seller_id (vendedor del
--                           subpedido; NULL en las órdenes anteriores = venta propia).
--   product_orders          offer_id: la línea vende la oferta de un vendedor externo (precio
--                           congelado de la oferta, sin reserva de bodega propia).
--   mp_offer_reservations   unidades de una oferta reservadas por una línea:
--                           ACTIVE → COMMITTED (pagada) / RELEASED / EXPIRED; COMMITTED → RETURNED.
--
-- No destructivo e idempotente: solo agrega tablas y columnas NULL. Las columnas de orders y
-- product_orders se agregan solo si esas tablas ya existen.
-- =============================================================================

CREATE TABLE IF NOT EXISTS purchases (
    id_purchase          BIGINT       NOT NULL AUTO_INCREMENT,
    company_id           BIGINT       NOT NULL,
    purchase_number      VARCHAR(20)  NULL,
    channel              VARCHAR(20)  NOT NULL,
    currency             CHAR(3)      NOT NULL,
    customer_name        VARCHAR(200) NULL,
    customer_doc_type    VARCHAR(10)  NULL,
    customer_doc_number  VARCHAR(30)  NULL,
    customer_email       VARCHAR(160) NULL,
    customer_phone       VARCHAR(40)  NULL,
    created_by           VARCHAR(64)  NOT NULL,
    created_by_name      VARCHAR(120) NULL,
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id_purchase),
    UNIQUE KEY uk_purchases_number (purchase_number),
    KEY ix_purchases_company (company_id, created_at),
    KEY ix_purchases_customer (company_id, customer_doc_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_offer_reservations (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    company_id     BIGINT      NOT NULL,
    offer_id       BIGINT      NOT NULL,
    order_id       BIGINT      NOT NULL,
    order_line_id  BIGINT      NOT NULL,
    quantity       INT         NOT NULL,
    status         VARCHAR(12) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_offer_reservations_line (order_line_id),
    KEY ix_mp_offer_reservations_order (order_id),
    KEY ix_mp_offer_reservations_offer (offer_id, status),
    CONSTRAINT fk_mp_offer_reservations_offer FOREIGN KEY (offer_id) REFERENCES mp_offers (id),
    CONSTRAINT ck_mp_offer_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_mp_offer_reservations_status CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED', 'RETURNED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

SET @has_orders := (SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders');
SET @has_lines := (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_orders');

-- 1. orders: compra y vendedor del subpedido ------------------------------------
SET @missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'purchase_id');
SET @sql := IF(@has_orders = 1 AND @missing = 1,
  'ALTER TABLE orders ADD COLUMN purchase_id BIGINT NULL,
                      ADD COLUMN seller_id BIGINT NULL,
                      ADD KEY ix_orders_purchase (purchase_id),
                      ADD KEY ix_orders_seller (seller_id),
                      ADD CONSTRAINT fk_orders_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id_purchase),
                      ADD CONSTRAINT fk_orders_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. product_orders: oferta vendida ----------------------------------------------
SET @missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_orders' AND COLUMN_NAME = 'offer_id');
SET @sql := IF(@has_lines = 1 AND @missing = 1,
  'ALTER TABLE product_orders ADD COLUMN offer_id BIGINT NULL,
                              ADD KEY ix_product_orders_offer (offer_id),
                              ADD CONSTRAINT fk_product_orders_offer FOREIGN KEY (offer_id) REFERENCES mp_offers (id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
