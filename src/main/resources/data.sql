-- Datos de referencia que necesita la aplicación en una base nueva (antes los cargaban las migraciones V7–V30).
-- Hibernate crea las tablas desde las entidades (ddl-auto) y Spring ejecuta este archivo después en cada arranque
-- (spring.jpa.defer-datasource-initialization=true). Todo es idempotente: solo inserta lo que falta, nunca
-- modifica ni borra datos existentes. Compatible con MySQL 8 y MariaDB.

-- Unidades de medida estándar (V7). Si el nombre ya lo usa otra unidad, se agrega "(código)".
INSERT INTO units_measurement (usr_crea, deleted, name, code, symbol, dimension, factor, is_base, display_decimals, active, created_at)
SELECT 1, 0,
       IF(EXISTS (SELECT 1 FROM units_measurement x WHERE x.deleted = 0 AND LOWER(x.name) = LOWER(s.name)),
          CONCAT(s.name, ' (', s.code, ')'), s.name),
       s.code, s.symbol, s.dimension, s.factor, s.is_base, s.display_decimals, 1, NOW(6)
  FROM (SELECT 'mm' code, 'mm' symbol, 'Milímetro' name, 'LENGTH' dimension, 0.001 factor, 0 is_base, 0 display_decimals
    UNION ALL SELECT 'cm', 'cm', 'Centímetro', 'LENGTH', 0.01, 0, 1
    UNION ALL SELECT 'm', 'm', 'Metro', 'LENGTH', 1, 1, 2
    UNION ALL SELECT 'km', 'km', 'Kilómetro', 'LENGTH', 1000, 0, 3
    UNION ALL SELECT 'in', 'in', 'Pulgada', 'LENGTH', 0.0254, 0, 2
    UNION ALL SELECT 'ft', 'ft', 'Pie', 'LENGTH', 0.3048, 0, 2
    UNION ALL SELECT 'mg', 'mg', 'Miligramo', 'MASS', 0.000001, 0, 0
    UNION ALL SELECT 'g', 'g', 'Gramo', 'MASS', 0.001, 0, 1
    UNION ALL SELECT 'kg', 'kg', 'Kilogramo', 'MASS', 1, 1, 3
    UNION ALL SELECT 't', 't', 'Tonelada', 'MASS', 1000, 0, 3
    UNION ALL SELECT 'oz', 'oz', 'Onza', 'MASS', 0.028349523125, 0, 2
    UNION ALL SELECT 'lb', 'lb', 'Libra', 'MASS', 0.45359237, 0, 2
    UNION ALL SELECT 'ml', 'ml', 'Mililitro', 'VOLUME', 0.001, 0, 0
    UNION ALL SELECT 'cl', 'cl', 'Centilitro', 'VOLUME', 0.01, 0, 1
    UNION ALL SELECT 'l', 'L', 'Litro', 'VOLUME', 1, 1, 2
    UNION ALL SELECT 'm3', 'm³', 'Metro cúbico', 'VOLUME', 1000, 0, 3
    UNION ALL SELECT 'fl_oz', 'fl oz', 'Onza líquida', 'VOLUME', 0.0295735295625, 0, 2
    UNION ALL SELECT 'gal', 'gal', 'Galón (EE. UU.)', 'VOLUME', 3.785411784, 0, 2
    UNION ALL SELECT 'cm2', 'cm²', 'Centímetro cuadrado', 'AREA', 0.0001, 0, 1
    UNION ALL SELECT 'm2', 'm²', 'Metro cuadrado', 'AREA', 1, 1, 2
    UNION ALL SELECT 'und', 'und', 'Unidad', 'COUNT', 1, 1, 0
    UNION ALL SELECT 'par', 'par', 'Par', 'COUNT', 2, 0, 0
    UNION ALL SELECT 'dz', 'dz', 'Docena', 'COUNT', 12, 0, 0) s
 WHERE NOT EXISTS (SELECT 1 FROM units_measurement u WHERE u.code = s.code AND u.deleted = 0);

-- Medios de pago habituales (V8).
INSERT INTO payment_types (code, name, description, instructions, channel, kind, provider, active, position,
                           fee_percent, fee_fixed, requires_reference, allows_change, usr_crea, created_at, deleted)
SELECT s.code, s.name, s.description, s.instructions, s.channel, s.kind, s.provider, s.active, s.position,
       0, 0, s.requires_reference, s.allows_change, 1, NOW(6), 0
  FROM (
    SELECT 'CASH' code, 'Efectivo' name, 'Pago en efectivo en tienda o punto de venta' description,
              NULL instructions, 'PHYSICAL' channel, 'CASH' kind, NULL provider, 1 active, 10 position,
              0 requires_reference, 1 allows_change
       UNION ALL SELECT 'CARD_TERMINAL', 'Datáfono', 'Tarjeta débito o crédito en datáfono',
              'Registra el número de aprobación del voucher.', 'PHYSICAL', 'CARD_TERMINAL', NULL, 1, 20, 1, 0
       UNION ALL SELECT 'BANK_TRANSFER', 'Transferencia bancaria', 'Transferencia o consignación a la cuenta de la tienda',
              'Registra el número del comprobante y confírmalo al verlo en el banco.', 'VIRTUAL', 'BANK_TRANSFER', NULL, 1, 30, 1, 0
       UNION ALL SELECT 'DIGITAL_WALLET', 'Nequi / Daviplata', 'Billetera digital confirmada manualmente',
              'Registra el número de la transacción.', 'VIRTUAL', 'DIGITAL_WALLET', NULL, 1, 40, 1, 0
       UNION ALL SELECT 'CASH_ON_DELIVERY', 'Contraentrega', 'El cliente paga al recibir el pedido',
              NULL, 'PHYSICAL', 'CASH_ON_DELIVERY', NULL, 1, 50, 0, 1
       UNION ALL SELECT 'SANDBOX', 'Pasarela de pruebas', 'Simulador interno de pagos en línea (solo pruebas)',
              NULL, 'VIRTUAL', 'GATEWAY', 'SANDBOX', 1, 90, 0, 0
       UNION ALL SELECT 'WOMPI', 'Wompi', 'Tarjeta, PSE, Nequi y Bancolombia en línea',
              NULL, 'VIRTUAL', 'GATEWAY', 'WOMPI', 0, 60, 0, 0
       UNION ALL SELECT 'MERCADO_PAGO', 'Mercado Pago', 'Tarjeta, PSE y efectivo vía Mercado Pago',
              NULL, 'VIRTUAL', 'GATEWAY', 'MERCADO_PAGO', 0, 70, 0, 0
  ) s
 WHERE NOT EXISTS (SELECT 1 FROM payment_types t WHERE t.code = s.code AND t.deleted = 0);

-- Tarifas de impuesto (V9).
INSERT INTO tax_rates (code, name, description, kind, rate, is_default, active, position, usr_crea, created_at, deleted)
SELECT s.code, s.name, s.description, s.kind, s.rate, s.is_default, 1, s.position, 1, NOW(6), 0
  FROM (
    SELECT 'IVA_19' code, 'IVA 19 %' name, 'Tarifa general del IVA' description, 'IVA' kind, 19.000 rate, 1 is_default, 10 position
    UNION ALL SELECT 'IVA_5', 'IVA 5 %', 'Tarifa diferencial del IVA', 'IVA', 5.000, 0, 20
    UNION ALL SELECT 'IVA_0', 'IVA exento (0 %)', 'Bienes y servicios exentos: se facturan con IVA 0 %', 'IVA', 0.000, 0, 30
    UNION ALL SELECT 'EXCLUIDO', 'Excluido de IVA', 'No causan IVA', 'EXCLUDED', 0.000, 0, 40
    UNION ALL SELECT 'INC_8', 'Impuesto al consumo 8 %', 'Impuesto nacional al consumo (restaurantes, bares…)', 'INC', 8.000, 0, 50
  ) s
 WHERE NOT EXISTS (SELECT 1 FROM tax_rates t WHERE t.code = s.code AND t.deleted = 0);

-- Configuración de facturación (V9): régimen y ambiente de pruebas con el simulador.
INSERT INTO billing_settings (id_billing_settings, tax_regime, environment, provider, auto_invoice, usr_crea, created_at, deleted)
SELECT 1, 'RESPONSABLE_IVA', 'TEST', 'SIMULATOR', 0, 1, NOW(6), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM billing_settings WHERE id_billing_settings = 1);

-- Numeración del set de pruebas de habilitación de la DIAN y de notas crédito (V9, ambiente de pruebas).
INSERT INTO billing_resolutions (kind, prefix, resolution_number, resolution_date, technical_key, from_number, to_number,
                                 next_number, valid_from, valid_to, environment, active, version, usr_crea, created_at, deleted)
SELECT 'INVOICE', 'SETT', '18760000001', '2019-01-19', 'fc8eac422eba16e22ffd8c6f94b3f40a6e38162c', 1, 5000000, 1,
       '2019-01-19', '2030-01-19', 'TEST', 1, 0, 1, NOW(6), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM billing_resolutions WHERE kind = 'INVOICE' AND deleted = 0);
INSERT INTO billing_resolutions (kind, prefix, resolution_number, from_number, to_number, next_number, environment, active,
                                 version, usr_crea, created_at, deleted)
SELECT 'CREDIT_NOTE', 'NC', NULL, 1, 99999999, 1, 'TEST', 1, 0, 1, NOW(6), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM billing_resolutions WHERE kind = 'CREDIT_NOTE' AND deleted = 0);

-- Bodega principal (V4) si no hay ninguna.
INSERT INTO warehouses (name, code, address, active, is_main, warehouse_type, allow_sales, allow_online, allow_receiving,
                        fulfillment_priority, usr_crea, created_at, deleted)
SELECT 'Bodega principal', 'PRINCIPAL', NULL, 1, 1, 'ALMACEN', 1, 1, 1, 10, 1, NOW(6), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM warehouses WHERE deleted = 0);

-- Tienda principal con la bodega principal (V11) si no hay ninguna.
INSERT INTO stores (code, name, warehouse_id, ticket_footer, active, usr_crea, created_at, deleted)
SELECT 'TIENDA_1', 'Tienda principal',
       (SELECT w.id_warehouse FROM warehouses w WHERE w.deleted = 0 ORDER BY w.is_main DESC, w.id_warehouse LIMIT 1),
       '¡Gracias por tu compra!', 1, 1, NOW(6), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM stores WHERE deleted = 0);

-- Caja principal (V9) si no hay ninguna.
INSERT INTO cash_registers (code, name, location, active, opening_mode, blind_close, difference_tolerance, auto_invoice,
                            receipt_width, usr_crea, created_at, deleted)
SELECT 'CAJA_1', 'Caja principal', 'Tienda', 1, 'FREE', 0, 0, 0, '80MM', 1, NOW(6), 0 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM cash_registers WHERE deleted = 0);

-- Catálogos de devoluciones (V10).
INSERT INTO motive_devolutions (name, description, usr_crea, created_at, deleted)
SELECT s.name, s.description, 1, NOW(6), 0
  FROM (SELECT 'Producto defectuoso' name, 'No funciona o tiene fallas de fábrica' description
        UNION ALL SELECT 'Llegó dañado', 'Golpeado, roto o con el empaque deteriorado'
        UNION ALL SELECT 'Producto equivocado', 'Talla, color o referencia diferente a la pedida'
        UNION ALL SELECT 'No cumple lo esperado', 'No corresponde a la descripción o a las fotos'
        UNION ALL SELECT 'Retracto del cliente', 'Derecho de retracto (5 días hábiles, Ley 1480)') s
 WHERE NOT EXISTS (SELECT 1 FROM motive_devolutions m WHERE m.name = s.name);

INSERT INTO return_methods (name, description, instructions, active, usr_crea, created_at, deleted)
SELECT s.name, s.description, s.instructions, 1, 1, NOW(6), 0
  FROM (SELECT 'Entrega en tienda' name, 'El cliente trae el producto' description,
               'Presentar el número de la orden y el producto con sus accesorios.' instructions
        UNION ALL SELECT 'Recogida a domicilio', 'La tienda recoge el producto', 'Coordinar fecha y dirección con el cliente.'
        UNION ALL SELECT 'Envío por transportadora', 'El cliente lo envía', 'Enviar con la guía indicada y conservar el comprobante.') s
 WHERE NOT EXISTS (SELECT 1 FROM return_methods r WHERE r.name = s.name);

INSERT INTO refund_methods (name, description, policy, active, usr_crea, created_at, deleted)
SELECT s.name, s.description, s.policy, 1, 1, NOW(6), 0
  FROM (SELECT 'Mismo medio de pago' name, 'Se reembolsa sobre los pagos de la orden' description,
               'Tarjeta y pasarelas: el banco puede tardar varios días hábiles.' policy
        UNION ALL SELECT 'Efectivo en tienda', 'Se entrega el dinero en caja', 'Requiere caja abierta y firma del cliente.'
        UNION ALL SELECT 'Transferencia bancaria', 'Se transfiere a la cuenta del cliente', 'Pedir certificación bancaria del titular.'
        UNION ALL SELECT 'Saldo a favor', 'Sin devolución de dinero: queda como crédito del cliente', 'Registrar el monto de reembolso en 0.') s
 WHERE NOT EXISTS (SELECT 1 FROM refund_methods r WHERE r.name = s.name);

-- Consecutivo de órdenes (V16): continúa después de la última orden existente.
INSERT IGNORE INTO order_numbering (id, prefix, padding, next_number)
SELECT 1, 'ORD', 6, COALESCE(MAX(id_order), 0) + 1 FROM orders;

-- Catálogo de entidades para pagar a vendedores: participantes de Bre-B (V30).
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
