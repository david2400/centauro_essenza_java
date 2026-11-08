-- Costo promedio ponderado del inventario propio (por SKU, todas las bodegas).
--   inv_sku_costs          promedio vigente, último costo de compra y recepción que lo dejó.
--   inv_sku_cost_history   cada recálculo: unidades y promedio antes, cantidad y costo recibidos, promedio después.
--
-- Las recepciones de órdenes de compra lo actualizan. No modifica product_skus.cost_price (el
-- catálogo lo reescribe al guardar el producto); los reportes de valor usan el promedio y, si no
-- lo hay, el costo del catálogo.
--
-- No destructivo e idempotente: solo crea tablas. La FK a product_skus se agrega si esa tabla existe.

CREATE TABLE IF NOT EXISTS inv_sku_costs (
    sku_id           BIGINT        NOT NULL,
    product_id       BIGINT        NOT NULL,
    avg_cost         DECIMAL(19,4) NULL,
    last_cost        DECIMAL(19,2) NULL,
    last_receipt_id  BIGINT        NULL,
    updated_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (sku_id),
    KEY ix_inv_sku_costs_product (product_id),
    CONSTRAINT ck_inv_sku_costs_values CHECK ((avg_cost IS NULL OR avg_cost >= 0) AND (last_cost IS NULL OR last_cost >= 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS inv_sku_cost_history (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    sku_id        BIGINT        NOT NULL,
    receipt_id    BIGINT        NOT NULL,
    units_before  BIGINT        NOT NULL,
    avg_before    DECIMAL(19,4) NULL,
    quantity      INT           NOT NULL,
    unit_cost     DECIMAL(19,2) NOT NULL,
    avg_after     DECIMAL(19,4) NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_sku_cost_history_receipt (receipt_id, sku_id),
    KEY ix_inv_sku_cost_history_sku (sku_id, id),
    CONSTRAINT fk_inv_sku_cost_history_sku FOREIGN KEY (sku_id) REFERENCES inv_sku_costs (sku_id),
    CONSTRAINT fk_inv_sku_cost_history_receipt FOREIGN KEY (receipt_id) REFERENCES inv_purchase_receipts (id),
    CONSTRAINT ck_inv_sku_cost_history_qty CHECK (quantity > 0 AND unit_cost >= 0 AND avg_after >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

SET @ok := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_skus')
         * (SELECT COUNT(*) = 0 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'inv_sku_costs' AND CONSTRAINT_NAME = 'fk_inv_sku_costs_sku');
SET @sql := IF(@ok = 1, 'ALTER TABLE inv_sku_costs ADD CONSTRAINT fk_inv_sku_costs_sku
                          FOREIGN KEY (sku_id) REFERENCES product_skus (id_sku)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
