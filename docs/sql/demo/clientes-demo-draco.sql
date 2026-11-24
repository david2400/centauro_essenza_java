-- Clientes de prueba para la lista Ventas → Clientes del panel (tabla `customers` de draco).
-- Base: la de draco (la misma donde están `orders` y `payment_methods`).
-- 1) Cambia @company por el id de la empresa con la que entras al panel.
-- 2) Es repetible: si el documento ya existe para esa empresa, no lo duplica.
-- Para borrarlos después: DELETE FROM customers WHERE company_id = @company AND created_by = 'seed-demo';

SET @company = 1;
SET @now = UTC_TIMESTAMP(6);

INSERT INTO customers (company_id, kind, doc_type, doc_number, name, email, phone, billing_address, billing_city,
                       store_user_id, marketing_consent, consent_at, notes, source, lock_version,
                       created_at, created_by, updated_at, updated_by)
VALUES
  (@company, 'PERSON',  'CC',  '1020304050', 'Ana María Pérez Gómez',      'ana.perez@example.com',      '3001234567', 'Calle 10 # 20-30 Apto 301',      'Medellín',     NULL, 1, @now, 'Cliente frecuente',                 'PANEL', 0, @now - INTERVAL 40 DAY, 'seed-demo', @now - INTERVAL 40 DAY, 'seed-demo'),
  (@company, 'PERSON',  'CC',  '1036789012', 'Carlos Andrés Restrepo',     'carlos.restrepo@example.com','3109876543', 'Carrera 43A # 1-50',             'Medellín',     NULL, 0, NULL, NULL,                                'PANEL', 0, @now - INTERVAL 35 DAY, 'seed-demo', @now - INTERVAL 35 DAY, 'seed-demo'),
  (@company, 'PERSON',  'CC',  '52345678',   'Luisa Fernanda Ríos',        'luisa.rios@example.com',     '3157654321', 'Avenida 19 # 104-37',            'Bogotá',       NULL, 1, @now, NULL,                                'PANEL', 0, @now - INTERVAL 30 DAY, 'seed-demo', @now - INTERVAL 30 DAY, 'seed-demo'),
  (@company, 'PERSON',  'CE',  '987654',     'Valentina Herrera (Venezuela)','valentina.h@example.com',  '3201112233', 'Calle 5 # 38-25',                'Cali',         NULL, 0, NULL, 'Cédula de extranjería',             'PANEL', 0, @now - INTERVAL 25 DAY, 'seed-demo', @now - INTERVAL 25 DAY, 'seed-demo'),
  (@company, 'PERSON',  'CC',  '79456123',   'Jorge Iván Martínez',        'jorge.martinez@example.com', '3014445566', 'Transversal 6 # 45-12',          'Bogotá',       NULL, 0, NULL, NULL,                                'ORDERS',0, @now - INTERVAL 20 DAY, 'seed-demo', @now - INTERVAL 20 DAY, 'seed-demo'),
  (@company, 'PERSON',  'CC',  '1152223344', 'Sofía Castaño Londoño',      'sofia.castano@example.com',  '3168889900', 'Calle 50 # 70-15',               'Envigado',     NULL, 1, @now, 'Prefiere entrega en la tarde',      'ORDERS',0, @now - INTERVAL 15 DAY, 'seed-demo', @now - INTERVAL 15 DAY, 'seed-demo'),
  (@company, 'PERSON',  'PP',  'AB1234567',  'Daniel Smith',               'daniel.smith@example.com',   '3005550101', 'Carrera 7 # 72-41 Hotel',        'Bogotá',       NULL, 0, NULL, 'Pasaporte, turista',                'PANEL', 0, @now - INTERVAL 12 DAY, 'seed-demo', @now - INTERVAL 12 DAY, 'seed-demo'),
  (@company, 'PERSON',  'CC',  '43567890',   'Paula Andrea Gaviria',       NULL,                         '3042223344', NULL,                             'Bello',        NULL, 0, NULL, 'Sin correo: solo WhatsApp',         'PANEL', 0, @now - INTERVAL 10 DAY, 'seed-demo', @now - INTERVAL 10 DAY, 'seed-demo'),
  (@company, 'COMPANY', 'NIT', '900123456',  'Distribuidora El Poblado S.A.S.', 'compras@elpoblado.example.com', '6044441122', 'Calle 10 Sur # 48-20 Bodega 3', 'Medellín', NULL, 1, @now, 'Factura electrónica a compras@',   'PANEL', 0, @now - INTERVAL 8 DAY,  'seed-demo', @now - INTERVAL 8 DAY,  'seed-demo'),
  (@company, 'COMPANY', 'NIT', '901987654',  'Belleza Natural Ltda.',      'pagos@bellezanatural.example.com', '6013337788', 'Carrera 15 # 93-60 Of. 502', 'Bogotá',  NULL, 0, NULL, 'Pago a 30 días',                    'PANEL', 0, @now - INTERVAL 5 DAY,  'seed-demo', @now - INTERVAL 5 DAY,  'seed-demo'),
  (@company, 'PERSON',  'CC',  '1001122334', 'Mateo Ospina Vélez',         'mateo.ospina@example.com',   '3176667788', 'Calle 30 # 65-80',               'Itagüí',       NULL, 1, @now, NULL,                                'ORDERS',0, @now - INTERVAL 2 DAY,  'seed-demo', @now - INTERVAL 2 DAY,  'seed-demo'),
  (@company, 'PERSON',  'CC',  '1098765432', 'Camila Torres Arango',       'camila.torres@example.com',  '3189990011', 'Carrera 80 # 33-10 Casa 4',      'Medellín',     NULL, 0, NULL, NULL,                                'PANEL', 0, @now - INTERVAL 1 HOUR, 'seed-demo', @now - INTERVAL 1 HOUR, 'seed-demo')
ON DUPLICATE KEY UPDATE id_customer = id_customer;

-- Verificación
SELECT id_customer, kind, doc_type, doc_number, name, email, source, created_at
FROM customers
WHERE company_id = @company AND created_by = 'seed-demo'
ORDER BY created_at DESC;
