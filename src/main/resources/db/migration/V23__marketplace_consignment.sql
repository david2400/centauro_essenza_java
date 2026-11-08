-- Consignación: mercancía de vendedores externos custodiada en bodegas propias.
--   mp_consignment_stock      saldo físico por empresa, vendedor (propietario comercial), SKU y bodega
--                             (custodio y ubicación). No se mezcla con stock_items (inventario propio).
--   mp_consignment_documents  recepciones, devoluciones al vendedor y ajustes de conteo (CSG-000001).
--   mp_consignment_movements  kardex con cantidad con signo: documentos, ventas, cancelaciones y
--                             devoluciones de clientes.
--
-- No destructivo e idempotente: solo crea tablas nuevas. Las FK a warehouses se agregan si existe.

CREATE TABLE IF NOT EXISTS mp_consignment_stock (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    company_id         BIGINT       NOT NULL,
    seller_id          BIGINT       NOT NULL,
    sku_id             BIGINT       NOT NULL,
    warehouse_id       BIGINT       NOT NULL,
    on_hand            INT          NOT NULL DEFAULT 0,
    first_received_at  DATETIME(6)  NULL,
    last_movement_at   DATETIME(6)  NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_consignment_stock (company_id, seller_id, sku_id, warehouse_id),
    KEY ix_mp_consignment_stock_sku (sku_id),
    KEY ix_mp_consignment_stock_warehouse (warehouse_id),
    CONSTRAINT fk_mp_consignment_stock_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id),
    CONSTRAINT fk_mp_consignment_stock_sku FOREIGN KEY (sku_id) REFERENCES product_skus (id_sku),
    CONSTRAINT ck_mp_consignment_stock_on_hand CHECK (on_hand >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_consignment_documents (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    document_number  VARCHAR(20)   NULL,
    kind             VARCHAR(20)   NOT NULL,
    seller_id        BIGINT        NOT NULL,
    warehouse_id     BIGINT        NOT NULL,
    reference        VARCHAR(80)   NULL,
    notes            VARCHAR(500)  NULL,
    units            INT           NOT NULL,
    created_by       VARCHAR(64)   NOT NULL,
    created_by_name  VARCHAR(120)  NULL,
    created_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_consignment_documents_number (company_id, document_number),
    KEY ix_mp_consignment_documents_seller (company_id, seller_id, created_at),
    CONSTRAINT fk_mp_consignment_documents_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id),
    CONSTRAINT ck_mp_consignment_documents_kind CHECK (kind IN ('RECEIPT', 'RETURN_TO_SELLER', 'ADJUSTMENT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS mp_consignment_movements (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    company_id      BIGINT        NOT NULL,
    stock_id        BIGINT        NOT NULL,
    seller_id       BIGINT        NOT NULL,
    sku_id          BIGINT        NOT NULL,
    warehouse_id    BIGINT        NOT NULL,
    kind            VARCHAR(20)   NOT NULL,
    quantity        INT           NOT NULL,
    balance_after   INT           NOT NULL,
    document_id     BIGINT        NULL,
    order_id        BIGINT        NULL,
    order_line_id   BIGINT        NULL,
    return_case_id  BIGINT        NULL,
    note            VARCHAR(500)  NULL,
    created_by      VARCHAR(64)   NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_mp_consignment_movements_stock (stock_id, id),
    KEY ix_mp_consignment_movements_seller (company_id, seller_id, created_at),
    KEY ix_mp_consignment_movements_order (order_id, order_line_id),
    KEY ix_mp_consignment_movements_document (document_id),
    CONSTRAINT fk_mp_consignment_movements_stock FOREIGN KEY (stock_id) REFERENCES mp_consignment_stock (id),
    CONSTRAINT fk_mp_consignment_movements_document FOREIGN KEY (document_id) REFERENCES mp_consignment_documents (id),
    CONSTRAINT ck_mp_consignment_movements_kind CHECK (kind IN ('RECEIPT', 'RETURN_TO_SELLER', 'ADJUSTMENT', 'SALE',
                                                                'SALE_CANCEL', 'CUSTOMER_RETURN')),
    CONSTRAINT ck_mp_consignment_movements_quantity CHECK (quantity <> 0 AND balance_after >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

SET @has_wh := (SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouses');
SET @missing := (SELECT COUNT(*) = 0 FROM information_schema.TABLE_CONSTRAINTS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mp_consignment_stock'
                   AND CONSTRAINT_NAME = 'fk_mp_consignment_stock_warehouse');
SET @sql := IF(@has_wh = 1 AND @missing = 1,
  'ALTER TABLE mp_consignment_stock ADD CONSTRAINT fk_mp_consignment_stock_warehouse
     FOREIGN KEY (warehouse_id) REFERENCES warehouses (id_warehouse)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
