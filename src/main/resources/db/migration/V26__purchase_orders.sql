-- Órdenes de compra a proveedores y su recepción en bodega (inventario propio).
--   inv_purchase_orders          OC-000001: proveedor, bodega destino, estado y fechas.
--   inv_purchase_order_lines     SKU, cantidad pedida y recibida, costo unitario sin impuestos.
--   inv_purchase_receipts        REC-000001: cada recepción (remisión del proveedor).
--   inv_purchase_receipt_lines   cantidades recibidas por línea.
--   inv_purchase_order_events    historial.
--
-- No destructivo e idempotente: solo crea tablas. Las FK a suppliers, warehouses y product_skus
-- se agregan si esas tablas existen.

CREATE TABLE IF NOT EXISTS inv_purchase_orders (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    order_number     VARCHAR(20)   NULL,
    supplier_id      BIGINT        NOT NULL,
    warehouse_id     BIGINT        NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    currency         CHAR(3)       NOT NULL DEFAULT 'COP',
    expected_date    DATE          NULL,
    notes            VARCHAR(1000) NULL,
    total            DECIMAL(19,2) NOT NULL DEFAULT 0,
    status_reason    VARCHAR(500)  NULL,
    sent_at          DATETIME(6)   NULL,
    closed_at        DATETIME(6)   NULL,
    lock_version     INT           NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NOT NULL,
    created_by_name  VARCHAR(120)  NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_purchase_orders_number (company_id, order_number),
    KEY ix_inv_purchase_orders_status (company_id, status, expected_date),
    KEY ix_inv_purchase_orders_supplier (supplier_id),
    CONSTRAINT ck_inv_purchase_orders_status CHECK (status IN ('DRAFT', 'SENT', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED', 'CLOSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS inv_purchase_order_lines (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    order_id           BIGINT        NOT NULL,
    sku_id             BIGINT        NOT NULL,
    product_id         BIGINT        NOT NULL,
    sku_code           VARCHAR(80)   NULL,
    product_name       VARCHAR(300)  NULL,
    supplier_code      VARCHAR(60)   NULL,
    quantity           INT           NOT NULL,
    received_quantity  INT           NOT NULL DEFAULT 0,
    unit_cost          DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_purchase_order_lines_sku (order_id, sku_id),
    CONSTRAINT fk_inv_purchase_order_lines_order FOREIGN KEY (order_id) REFERENCES inv_purchase_orders (id),
    CONSTRAINT ck_inv_purchase_order_lines_qty CHECK (quantity > 0 AND received_quantity >= 0 AND received_quantity <= quantity
                                                      AND unit_cost >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS inv_purchase_receipts (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    company_id       BIGINT        NOT NULL,
    receipt_number   VARCHAR(20)   NULL,
    order_id         BIGINT        NOT NULL,
    warehouse_id     BIGINT        NOT NULL,
    reference        VARCHAR(80)   NULL,
    notes            VARCHAR(500)  NULL,
    units            INT           NOT NULL,
    created_by       VARCHAR(64)   NOT NULL,
    created_by_name  VARCHAR(120)  NULL,
    created_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_purchase_receipts_number (company_id, receipt_number),
    KEY ix_inv_purchase_receipts_order (order_id),
    CONSTRAINT fk_inv_purchase_receipts_order FOREIGN KEY (order_id) REFERENCES inv_purchase_orders (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS inv_purchase_receipt_lines (
    id          BIGINT  NOT NULL AUTO_INCREMENT,
    receipt_id  BIGINT  NOT NULL,
    line_id     BIGINT  NOT NULL,
    quantity    INT     NOT NULL,
    PRIMARY KEY (id),
    KEY ix_inv_purchase_receipt_lines_receipt (receipt_id),
    CONSTRAINT fk_inv_purchase_receipt_lines_receipt FOREIGN KEY (receipt_id) REFERENCES inv_purchase_receipts (id),
    CONSTRAINT fk_inv_purchase_receipt_lines_line FOREIGN KEY (line_id) REFERENCES inv_purchase_order_lines (id),
    CONSTRAINT ck_inv_purchase_receipt_lines_qty CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS inv_purchase_order_events (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_id     BIGINT        NOT NULL,
    action       VARCHAR(20)   NOT NULL,
    from_status  VARCHAR(20)   NULL,
    to_status    VARCHAR(20)   NOT NULL,
    note         VARCHAR(1000) NULL,
    actor_id     VARCHAR(64)   NOT NULL,
    actor_name   VARCHAR(120)  NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_inv_purchase_order_events_order (order_id, id),
    CONSTRAINT fk_inv_purchase_order_events_order FOREIGN KEY (order_id) REFERENCES inv_purchase_orders (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- FK opcionales (solo si las tablas referenciadas existen y la FK aún no está) -------------------
SET @ok := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suppliers')
         * (SELECT COUNT(*) = 0 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'inv_purchase_orders' AND CONSTRAINT_NAME = 'fk_inv_purchase_orders_supplier');
SET @sql := IF(@ok = 1, 'ALTER TABLE inv_purchase_orders ADD CONSTRAINT fk_inv_purchase_orders_supplier
                          FOREIGN KEY (supplier_id) REFERENCES suppliers (id_supplier)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ok := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouses')
         * (SELECT COUNT(*) = 0 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'inv_purchase_orders' AND CONSTRAINT_NAME = 'fk_inv_purchase_orders_warehouse');
SET @sql := IF(@ok = 1, 'ALTER TABLE inv_purchase_orders ADD CONSTRAINT fk_inv_purchase_orders_warehouse
                          FOREIGN KEY (warehouse_id) REFERENCES warehouses (id_warehouse)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ok := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_skus')
         * (SELECT COUNT(*) = 0 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'inv_purchase_order_lines' AND CONSTRAINT_NAME = 'fk_inv_purchase_order_lines_sku');
SET @sql := IF(@ok = 1, 'ALTER TABLE inv_purchase_order_lines ADD CONSTRAINT fk_inv_purchase_order_lines_sku
                          FOREIGN KEY (sku_id) REFERENCES product_skus (id_sku)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
