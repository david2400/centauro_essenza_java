-- Productos de prueba para la tienda. Versión 3: cada sentencia es independiente.
--  * Sin variables (@...) ni USE: las tablas van con el nombre de la base (storeweb.tabla),
--    así funciona aunque el editor tenga otra base seleccionada o ejecute las sentencias por separado.
--  * Sin INSERT IGNORE: si algo falla, MySQL muestra el error.
--  * Se puede correr varias veces: no duplica.
--  * Termina con COMMIT por si el editor está en modo de confirmación manual.
-- En DBeaver: Alt+X (ejecutar script completo).



-- 1) Categorías
INSERT INTO storeweb.categories (name, description, slug, usr_crea, created_at)
SELECT 'Cuidado facial', 'Limpieza, hidratación y protección del rostro', 'cuidado-facial', 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.categories WHERE name = 'Cuidado facial');
INSERT INTO storeweb.categories (name, description, slug, usr_crea, created_at)
SELECT 'Cuidado corporal', 'Cremas, aceites y exfoliantes para el cuerpo', 'cuidado-corporal', 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.categories WHERE name = 'Cuidado corporal');
INSERT INTO storeweb.categories (name, description, slug, usr_crea, created_at)
SELECT 'Fragancias', 'Perfumes y colonias', 'fragancias', 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.categories WHERE name = 'Fragancias');


-- 2) Subcategorías
INSERT INTO storeweb.subcategorys (name, description, slug, category_id, usr_crea, created_at)
SELECT 'Sérums', 'Sérums faciales', 'serums', (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado facial' LIMIT 1), 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.subcategorys WHERE name = 'Sérums');
INSERT INTO storeweb.subcategorys (name, description, slug, category_id, usr_crea, created_at)
SELECT 'Hidratantes', 'Cremas hidratantes', 'hidratantes', (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado facial' LIMIT 1), 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.subcategorys WHERE name = 'Hidratantes');
INSERT INTO storeweb.subcategorys (name, description, slug, category_id, usr_crea, created_at)
SELECT 'Protector solar', 'Protección UV', 'protector-solar', (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado facial' LIMIT 1), 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.subcategorys WHERE name = 'Protector solar');
INSERT INTO storeweb.subcategorys (name, description, slug, category_id, usr_crea, created_at)
SELECT 'Cremas corporales', 'Cremas para el cuerpo', 'cremas-corporales', (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado corporal' LIMIT 1), 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.subcategorys WHERE name = 'Cremas corporales');
INSERT INTO storeweb.subcategorys (name, description, slug, category_id, usr_crea, created_at)
SELECT 'Aceites', 'Aceites corporales', 'aceites', (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado corporal' LIMIT 1), 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.subcategorys WHERE name = 'Aceites');
INSERT INTO storeweb.subcategorys (name, description, slug, category_id, usr_crea, created_at)
SELECT 'Perfumes', 'Perfumes y eau de parfum', 'perfumes', (SELECT id_category FROM storeweb.categories WHERE name = 'Fragancias' LIMIT 1), 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.subcategorys WHERE name = 'Perfumes');


-- 3) Marcas
INSERT INTO storeweb.brands (name, description, slug, usr_crea, created_at)
SELECT 'Essenza Natural', 'Línea propia de cuidado natural', 'essenza-natural', 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.brands WHERE name = 'Essenza Natural');
INSERT INTO storeweb.brands (name, description, slug, usr_crea, created_at)
SELECT 'Botánica Andina', 'Ingredientes de origen andino', 'botanica-andina', 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.brands WHERE name = 'Botánica Andina');


-- 4) Proveedor y bodega de venta en línea
INSERT INTO storeweb.suppliers (code, doc_type, doc_number, dv, person_type, name, great_contributor, self_retainer,
                       status, payment_term_days, currency, usr_crea, created_at)
SELECT 'DEMO-PROV', 'NIT', '900555111', '3', 'JURIDICA', 'Proveedor Demo S.A.S.', 0, 0, 'ACTIVE', 30, 'COP', 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.suppliers WHERE code = 'DEMO-PROV' AND deleted = 0);

INSERT INTO storeweb.warehouses (name, code, address, active, is_main, warehouse_type, allow_sales, allow_online,
                        allow_receiving, fulfillment_priority, city, usr_crea, created_at)
SELECT 'Bodega tienda en línea (demo)', 'DEMO-ONLINE', 'Calle 10 # 43-20', 1, 0, 'ALMACEN', 1, 1, 1, 1, 'Medellín', 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM storeweb.warehouses WHERE code = 'DEMO-ONLINE' AND deleted = 0);

-- 5) Productos
INSERT INTO storeweb.products (name, description, stock, real_price, unit_price, status, slug, product_type, image_url,
                      available, brand_id, category_id, subcategory_id, supplier_id, is_combo, usr_crea, created_at)
SELECT v.name, v.description, v.stock, v.price, v.price, 'ACTIVE', v.slug, v.ptype,
       CONCAT('https://picsum.photos/seed/', v.slug, '/800/800'), 1, v.brand, v.cat, v.sub, (SELECT id_supplier FROM storeweb.suppliers WHERE code = 'DEMO-PROV' AND deleted = 0 LIMIT 1), 0, 1, NOW()
FROM (
  SELECT 'Sérum de vitamina C 30 ml' AS name, 'Ilumina y unifica el tono. Uso diario en la mañana.' AS description, 40 AS stock, 89900 AS price, 'serum-vitamina-c-30ml' AS slug, 'SIMPLE' AS ptype, (SELECT id_brand FROM storeweb.brands WHERE name = 'Essenza Natural' LIMIT 1) AS brand, (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado facial' LIMIT 1) AS cat, (SELECT id_subcategory FROM storeweb.subcategorys WHERE name = 'Sérums' LIMIT 1) AS sub
  UNION ALL SELECT 'Sérum de ácido hialurónico 30 ml', 'Hidratación profunda para todo tipo de piel.', 35, 79900, 'serum-acido-hialuronico', 'SIMPLE', (SELECT id_brand FROM storeweb.brands WHERE name = 'Essenza Natural' LIMIT 1), (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado facial' LIMIT 1), (SELECT id_subcategory FROM storeweb.subcategorys WHERE name = 'Sérums' LIMIT 1)
  UNION ALL SELECT 'Crema hidratante facial', 'Textura ligera, no grasa. Disponible en dos tamaños.', 60, 54900, 'crema-hidratante-facial', 'VARIANT', (SELECT id_brand FROM storeweb.brands WHERE name = 'Botánica Andina' LIMIT 1), (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado facial' LIMIT 1), (SELECT id_subcategory FROM storeweb.subcategorys WHERE name = 'Hidratantes' LIMIT 1)
  UNION ALL SELECT 'Protector solar FPS 50 50 ml', 'Toque seco, sin color. Resistente al agua.', 50, 64900, 'protector-solar-fps50', 'SIMPLE', (SELECT id_brand FROM storeweb.brands WHERE name = 'Essenza Natural' LIMIT 1), (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado facial' LIMIT 1), (SELECT id_subcategory FROM storeweb.subcategorys WHERE name = 'Protector solar' LIMIT 1)
  UNION ALL SELECT 'Crema corporal de karité 250 ml', 'Nutre la piel seca. Aroma suave a vainilla.', 45, 42900, 'crema-corporal-karite', 'SIMPLE', (SELECT id_brand FROM storeweb.brands WHERE name = 'Botánica Andina' LIMIT 1), (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado corporal' LIMIT 1), (SELECT id_subcategory FROM storeweb.subcategorys WHERE name = 'Cremas corporales' LIMIT 1)
  UNION ALL SELECT 'Aceite corporal de almendras 120 ml', 'Prensado en frío. Para después de la ducha.', 30, 38900, 'aceite-almendras-120ml', 'SIMPLE', (SELECT id_brand FROM storeweb.brands WHERE name = 'Botánica Andina' LIMIT 1), (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado corporal' LIMIT 1), (SELECT id_subcategory FROM storeweb.subcategorys WHERE name = 'Aceites' LIMIT 1)
  UNION ALL SELECT 'Eau de parfum Flor de café', 'Notas de café, jazmín y vainilla. Dos presentaciones.', 25, 149900, 'eau-de-parfum-flor-de-cafe', 'VARIANT', (SELECT id_brand FROM storeweb.brands WHERE name = 'Essenza Natural' LIMIT 1), (SELECT id_category FROM storeweb.categories WHERE name = 'Fragancias' LIMIT 1), (SELECT id_subcategory FROM storeweb.subcategorys WHERE name = 'Perfumes' LIMIT 1)
  UNION ALL SELECT 'Kit de viaje cuidado facial', 'Limpiador, sérum e hidratante en tamaño viaje.', 15, 69900, 'kit-viaje-cuidado-facial', 'SIMPLE', (SELECT id_brand FROM storeweb.brands WHERE name = 'Essenza Natural' LIMIT 1), (SELECT id_category FROM storeweb.categories WHERE name = 'Cuidado facial' LIMIT 1), (SELECT id_subcategory FROM storeweb.subcategorys WHERE name = 'Hidratantes' LIMIT 1)
) v
WHERE NOT EXISTS (SELECT 1 FROM storeweb.products p WHERE p.slug = v.slug);

-- 6) SKUs (compare_at_price = precio antes, para mostrar el descuento)
INSERT INTO storeweb.product_skus (product_id, code, name, price, cost_price, compare_at_price, is_default, active, version, usr_crea, created_at)
SELECT p.id_product, v.code, v.name, v.price, v.cost, v.compare_at, v.is_default, 1, 0, 1, NOW()
FROM (
  SELECT 'serum-vitamina-c-30ml' AS slug, 'DEMO-SVC-30' AS code, 'Sérum vitamina C 30 ml' AS name, 89900 AS price, 40000 AS cost, 109900 AS compare_at, 1 AS is_default
  UNION ALL SELECT 'serum-acido-hialuronico', 'DEMO-SAH-30', 'Sérum ácido hialurónico 30 ml', 79900, 35000, NULL, 1
  UNION ALL SELECT 'crema-hidratante-facial', 'DEMO-CHF-50', 'Crema hidratante 50 ml', 54900, 22000, NULL, 1
  UNION ALL SELECT 'crema-hidratante-facial', 'DEMO-CHF-100', 'Crema hidratante 100 ml', 89900, 36000, 99900, 0
  UNION ALL SELECT 'protector-solar-fps50', 'DEMO-PS50-50', 'Protector solar FPS 50 50 ml', 64900, 28000, NULL, 1
  UNION ALL SELECT 'crema-corporal-karite', 'DEMO-CCK-250', 'Crema corporal karité 250 ml', 42900, 17000, 49900, 1
  UNION ALL SELECT 'aceite-almendras-120ml', 'DEMO-AAL-120', 'Aceite de almendras 120 ml', 38900, 15000, NULL, 1
  UNION ALL SELECT 'eau-de-parfum-flor-de-cafe', 'DEMO-EDP-50', 'Eau de parfum 50 ml', 149900, 60000, NULL, 1
  UNION ALL SELECT 'eau-de-parfum-flor-de-cafe', 'DEMO-EDP-100', 'Eau de parfum 100 ml', 219900, 90000, 249900, 0
  UNION ALL SELECT 'kit-viaje-cuidado-facial', 'DEMO-KIT-VIAJE', 'Kit de viaje cuidado facial', 69900, 30000, 84900, 1
) v
JOIN storeweb.products p ON p.slug = v.slug AND p.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM storeweb.product_skus s WHERE s.code = v.code);

-- 7) Existencias en la bodega en línea
INSERT INTO storeweb.stock_items (sku_id, product_id, warehouse_id, on_hand, reserved, min_threshold, version, usr_crea, created_at)
SELECT s.id_sku, s.product_id, (SELECT id_warehouse FROM storeweb.warehouses WHERE code = 'DEMO-ONLINE' AND deleted = 0 LIMIT 1), q.qty, 0, 3, 0, 1, NOW()
FROM (
  SELECT 'DEMO-SVC-30' AS code, 40 AS qty
  UNION ALL SELECT 'DEMO-SAH-30', 35
  UNION ALL SELECT 'DEMO-CHF-50', 30
  UNION ALL SELECT 'DEMO-CHF-100', 30
  UNION ALL SELECT 'DEMO-PS50-50', 50
  UNION ALL SELECT 'DEMO-CCK-250', 45
  UNION ALL SELECT 'DEMO-AAL-120', 30
  UNION ALL SELECT 'DEMO-EDP-50', 15
  UNION ALL SELECT 'DEMO-EDP-100', 10
  UNION ALL SELECT 'DEMO-KIT-VIAJE', 2
) q
JOIN storeweb.product_skus s ON s.code = q.code AND s.deleted = 0
WHERE NOT EXISTS (SELECT 1 FROM storeweb.stock_items si WHERE si.sku_id = s.id_sku AND si.warehouse_id = (SELECT id_warehouse FROM storeweb.warehouses WHERE code = 'DEMO-ONLINE' AND deleted = 0 LIMIT 1));

-- 8) Imágenes de la galería (2 por producto)
INSERT INTO storeweb.product_images (product_id, url, position, alt_text, usr_crea, created_at)
SELECT p.id_product, CONCAT('https://picsum.photos/seed/', p.slug, '-', n.pos, '/800/800'), n.pos, p.name, 1, NOW()
FROM storeweb.products p
JOIN (SELECT 0 AS pos UNION ALL SELECT 1) n
WHERE p.slug IN ('serum-vitamina-c-30ml', 'serum-acido-hialuronico', 'crema-hidratante-facial', 'protector-solar-fps50',
                 'crema-corporal-karite', 'aceite-almendras-120ml', 'eau-de-parfum-flor-de-cafe', 'kit-viaje-cuidado-facial')
  AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM storeweb.product_images i WHERE i.product_id = p.id_product AND i.deleted = 0);

COMMIT;

-- 9) Verificación: deben salir 10 filas
SELECT p.id_product, p.name, s.code, s.price, s.compare_at_price, si.on_hand - si.reserved AS disponible
FROM storeweb.products p
JOIN storeweb.product_skus s ON s.product_id = p.id_product AND s.deleted = 0 AND s.active = 1
JOIN storeweb.stock_items si ON si.sku_id = s.id_sku AND si.deleted = 0
JOIN storeweb.warehouses w ON w.id_warehouse = si.warehouse_id AND w.allow_online = 1 AND w.active = 1
WHERE s.code LIKE 'DEMO-%'
ORDER BY p.id_product, s.price;
