-- Pagos a vendedores: cualquier banco, banco digital, cooperativa o billetera, y llaves Bre-B.
--   mp_banks                  catálogo de entidades (editable desde el panel; código de banco opcional).
--   mp_seller_bank_accounts   + entidad del catálogo, tipo de llave Bre-B y número/llave hasta 120 caracteres.
--   mp_payout_items           + código de banco y tipo de llave copiados al armar el lote.
--
-- El catálogo inicial trae las entidades más usadas que participan en Bre-B; los códigos de banco
-- (ACH) se dejan vacíos para que la tienda los llene según el formato de su banco pagador.
-- No destructivo e idempotente.

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
