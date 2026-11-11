# Migraciones de base de datos (Flyway)

> **Historial.** Flyway está apagado: el esquema ahora lo crea JPA (ver `../README.md`). Estos archivos ya no se ejecutan.
>
> **Solo datos.** Se quitaron las sentencias de esquema (`CREATE TABLE`, `ALTER TABLE`, índices, llaves y CHECK): el
> esquema lo crea JPA y lo que JPA no hace lo completa `config/schema/SchemaFinisher` al arrancar. Quedan los `INSERT` y
> `UPDATE` (migración y carga de datos) con las variables y tablas de trabajo que necesitan (`tmp_opening`,
> `v5_tmp_feature_codes`, `v7_unit_seed`). Los archivos sin datos se borraron: V2, V12, V17–V29 y V31–V33. La tabla de
> abajo describe lo que hacía cada versión completa.

- **V1** es la *línea base*: el esquema que ya existía cuando se activó Flyway
  (lo creó Hibernate con `ddl-auto=update`). No hay archivo V1: Flyway la marca
  con `baseline-on-migrate=true` y `baseline-version=1`.
- Desde **V2** cada cambio de esquema va en un archivo `V<n>__descripcion.sql`.
  Nunca edites una migración ya aplicada: crea una nueva.
- Plan: cuando todo el esquema esté cubierto por migraciones se pasará
  `ddl-auto` de `update` a `validate` también en desarrollo.

| Versión | Qué hace |
|---|---|
| V2 | Corrige los índices únicos de `stock_per_warehouse` (unicidad por pareja producto-bodega) |
| V3 | Catálogo (Fase 2): `products.status`/`slug`/`product_type`, precios DECIMAL(15,2), tablas `product_skus` y `product_images` con migración de los datos existentes (no destructiva) |
| V4 | Inventario (Fase 3): tabla `stock_items` (stock por SKU y bodega, fuente de verdad), `warehouses.is_main`, `inventory_movements.sku_id`/`reference_type`/`reference_id`; migra `stock_per_warehouse` y lleva la diferencia con la ficha a la bodega principal como movimiento de apertura (`MIGRATION`) |
| V5 | Atributos (Fase 4): `attributes` (tipados + unidad), `attribute_options`, `product_templates`, `template_attributes` (obligatorio, eje de variante, filtrable), `product_attribute_values`, `sku_attribute_values`, `products.template_id`; migra features/type_products/type_product_features/product_features (las tablas antiguas se conservan) |
| V6 | Ventas (Fase 5): dinero DECIMAL en `orders`/`product_orders`, `orders.stock_managed`/`cancel_reason`, líneas con `sku_id` y foto (`sku_code`, `product_name`, `unit_price`), tabla `stock_reservations`; estados normalizados a mayúsculas |
| V7 | Unidades de medida: `units_measurement.code`/`symbol`/`dimension`/`factor`/`is_base`/`display_decimals`/`active` (código y nombre únicos entre no borradas), unidades estándar precargadas (longitud, masa, volumen, área, cantidad; las existentes que coinciden se completan), contenido neto `net_content`/`net_content_unit_id` en `products` y `product_childs` |
