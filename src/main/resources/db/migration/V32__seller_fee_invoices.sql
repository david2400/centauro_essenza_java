-- Factura de la plataforma al vendedor (comisiones, mensualidad, almacenamiento y cargos por reclamo), mensual.
--   invoices.order_id           pasa a admitir nulo: facturas de servicios que no salen de una orden.
--   invoice_sources             origen de cada factura de servicios (tipo + clave) para no duplicarla.
--   mp_seller_invoices          facturas emitidas a cada vendedor por periodo (AAAA-MM).
--
-- Las ventas de vendedores externos que cobra la tienda las sigue facturando el vendedor (decisión del usuario).
-- No destructivo e idempotente.

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'invoices'
                  AND COLUMN_NAME = 'order_id' AND IS_NULLABLE = 'NO') = 1,
  'ALTER TABLE invoices MODIFY order_id BIGINT NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS invoice_sources (
    invoice_id   BIGINT       NOT NULL,
    source_type  VARCHAR(40)  NOT NULL,
    source_key   VARCHAR(80)  NOT NULL,
    PRIMARY KEY (invoice_id),
    KEY ix_invoice_sources_source (source_type, source_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'invoices'
                  AND COLUMN_NAME = 'id_invoice') = 1
             AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'invoice_sources' AND CONSTRAINT_NAME = 'fk_invoice_sources_invoice') = 0,
  'ALTER TABLE invoice_sources ADD CONSTRAINT fk_invoice_sources_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id_invoice)',
  'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS mp_seller_invoices (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    company_id      BIGINT         NOT NULL,
    seller_id       BIGINT         NOT NULL,
    period          CHAR(7)        NOT NULL,
    invoice_id      BIGINT         NOT NULL,
    invoice_number  VARCHAR(30)    NULL,
    total           DECIMAL(19,2)  NOT NULL,
    created_by      VARCHAR(64)    NOT NULL,
    created_by_name VARCHAR(120)   NULL,
    created_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_seller_invoices_invoice (invoice_id),
    KEY ix_mp_seller_invoices_period (company_id, period, seller_id),
    CONSTRAINT fk_mp_seller_invoices_seller FOREIGN KEY (seller_id) REFERENCES mp_sellers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
