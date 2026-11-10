-- Pagos a vendedores: cualquier banco, banco digital, cooperativa o billetera, y llaves Bre-B.
--   mp_banks                  catálogo de entidades (editable desde el panel; código de banco opcional).
--   mp_seller_bank_accounts   + entidad del catálogo, tipo de llave Bre-B y número/llave hasta 120 caracteres.
--   mp_payout_items           + código de banco y tipo de llave copiados al armar el lote.
--
-- El catálogo inicial trae las entidades más usadas que participan en Bre-B; los códigos de banco
-- (ACH) se dejan vacíos para que la tienda los llene según el formato de su banco pagador.
-- No destructivo e idempotente.

CREATE TABLE IF NOT EXISTS mp_banks (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    kind        VARCHAR(20)  NOT NULL,
    ach_code    VARCHAR(10)  NULL,
    active      TINYINT(1)   NOT NULL DEFAULT 1,
    position    INT          NOT NULL DEFAULT 100,
    updated_by  VARCHAR(64)  NULL,
    updated_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_banks_code (code),
    CONSTRAINT ck_mp_banks_kind CHECK (kind IN ('BANK', 'DIGITAL_BANK', 'WALLET', 'COOPERATIVE', 'FINANCIAL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT IGNORE INTO mp_banks (code, name, kind, position) VALUES
  ('BANCOLOMBIA', 'Bancolombia', 'BANK', 10),
  ('BANCO_BOGOTA', 'Banco de Bogotá', 'BANK', 11),
  ('DAVIVIENDA', 'Banco Davivienda', 'BANK', 12),
  ('BBVA', 'BBVA Colombia', 'BANK', 13),
  ('OCCIDENTE', 'Banco de Occidente', 'BANK', 14),
  ('POPULAR', 'Banco Popular', 'BANK', 15),
  ('AV_VILLAS', 'Banco AV Villas', 'BANK', 16),
  ('CAJA_SOCIAL', 'Banco Caja Social', 'BANK', 17),
  ('AGRARIO', 'Banco Agrario', 'BANK', 18),
  ('ITAU', 'Banco Itaú', 'BANK', 19),
  ('SANTANDER', 'Banco Santander', 'BANK', 20),
  ('SUDAMERIS', 'Banco Sudameris', 'BANK', 21),
  ('DAVIBANK', 'Davibank', 'BANK', 22),
  ('FALABELLA', 'Banco Falabella', 'BANK', 23),
  ('FINANDINA', 'Banco Finandina', 'BANK', 24),
  ('SERFINANZA', 'Banco Serfinanza', 'BANK', 25),
  ('BANCOOMEVA', 'Bancoomeva', 'BANK', 26),
  ('BTG_PACTUAL', 'Banco BTG Pactual', 'BANK', 27),
  ('MUNDO_MUJER', 'Banco Mundo Mujer', 'BANK', 28),
  ('BANCO_W', 'Banco W', 'BANK', 29),
  ('BANCAMIA', 'Bancamía', 'BANK', 30),
  ('CONTACTAR', 'Banco Contactar', 'BANK', 31),
  ('BANCO_UNION', 'Banco Unión', 'BANK', 32),
  ('COOPCENTRAL', 'Coopcentral', 'BANK', 33),
  ('NU', 'Nu Colombia', 'DIGITAL_BANK', 40),
  ('LULO', 'Lulo Bank', 'DIGITAL_BANK', 41),
  ('BAN100', 'Ban100', 'DIGITAL_BANK', 42),
  ('UALA', 'Ualá', 'DIGITAL_BANK', 43),
  ('RAPPIPAY', 'RappiPay', 'DIGITAL_BANK', 44),
  ('MONO', 'Mono Colombia', 'DIGITAL_BANK', 45),
  ('NEQUI', 'Nequi', 'WALLET', 50),
  ('DAVIPLATA', 'Daviplata', 'WALLET', 51),
  ('MOVII', 'Movii', 'WALLET', 52),
  ('DALE', 'Dale!', 'WALLET', 53),
  ('POWWI', 'Powwi', 'WALLET', 54),
  ('COINK', 'Coink', 'WALLET', 55),
  ('CONFIAR', 'Confiar Cooperativa Financiera', 'COOPERATIVE', 60),
  ('COTRAFA', 'Cotrafa', 'COOPERATIVE', 61),
  ('JFK', 'JFK Cooperativa Financiera', 'COOPERATIVE', 62),
  ('COMULTRASAN', 'Comultrasan', 'COOPERATIVE', 63),
  ('COLTEFINANCIERA', 'Coltefinanciera', 'FINANCIAL', 70),
  ('FINANCIERA_COFINCAFE', 'Financiera Cofincafé', 'FINANCIAL', 71);

-- Cuentas de vendedores: entidad, llave Bre-B y número largo -------------------------------------
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'mp_seller_bank_accounts' AND COLUMN_NAME = 'bank_id') = 0,
  'ALTER TABLE mp_seller_bank_accounts ADD COLUMN bank_id BIGINT NULL AFTER company_id,
     ADD COLUMN key_type VARCHAR(20) NULL AFTER account_type, MODIFY account_number VARCHAR(120) NOT NULL,
     ADD CONSTRAINT fk_mp_seller_bank_accounts_bank FOREIGN KEY (bank_id) REFERENCES mp_banks (id)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'mp_seller_bank_accounts' AND CONSTRAINT_NAME = 'ck_mp_seller_bank_accounts_type') = 1,
  CONCAT('ALTER TABLE mp_seller_bank_accounts DROP ', IF(VERSION() LIKE '%MariaDB%', 'CONSTRAINT', 'CHECK'), ' ck_mp_seller_bank_accounts_type'), 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE mp_seller_bank_accounts ADD CONSTRAINT ck_mp_seller_bank_accounts_type
    CHECK (account_type IN ('SAVINGS', 'CHECKING', 'DIGITAL_WALLET', 'BRE_B_KEY'));

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'mp_payout_items' AND COLUMN_NAME = 'bank_code') = 0,
  'ALTER TABLE mp_payout_items ADD COLUMN bank_code VARCHAR(10) NULL AFTER bank_name,
     ADD COLUMN key_type VARCHAR(20) NULL AFTER account_type, MODIFY account_number VARCHAR(120) NOT NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
