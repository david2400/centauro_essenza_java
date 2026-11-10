-- Formatos del archivo de pagos para el banco pagador de la tienda (cualquier banco).
--   mp_payout_formats  nombre, extensión, codificación y diseño (JSON: tipo delimitado o ancho fijo,
--                      registros de encabezado, detalle y control, columnas y tablas de códigos).
-- El formato genérico (CSV) no se guarda: lo trae el código. No destructivo e idempotente.

CREATE TABLE IF NOT EXISTS mp_payout_formats (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    name             VARCHAR(80)   NOT NULL,
    file_extension   VARCHAR(5)    NOT NULL,
    encoding         VARCHAR(20)   NOT NULL,
    layout_json      TEXT          NOT NULL,
    active           TINYINT(1)    NOT NULL DEFAULT 1,
    updated_by       VARCHAR(64)   NOT NULL,
    updated_by_name  VARCHAR(120)  NULL,
    updated_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_payout_formats_name (name),
    CONSTRAINT ck_mp_payout_formats_encoding CHECK (encoding IN ('UTF-8', 'ISO-8859-1', 'windows-1252')),
    CONSTRAINT ck_mp_payout_formats_ext CHECK (file_extension IN ('csv', 'txt', 'prn', 'dat'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
