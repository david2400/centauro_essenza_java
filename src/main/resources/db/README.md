# Esquema de la base de datos

**JPA es la fuente del esquema.** Hibernate crea y actualiza las tablas desde las entidades:

- Entidades de los módulos (`*/infrastructure/outbound/persistence/...`), usadas también por los repositorios JPA.
- Entidades de **solo esquema** (`*/infrastructure/outbound/persistence/schema/*Entity`): tablas que se leen y
  escriben con JDBC (marketplace, compras, costo promedio, pagos agrupados, clientes, etc.). Solo existen para que
  Hibernate cree la tabla con sus columnas, únicos, índices, CHECK y llaves foráneas; no se usan en consultas.
- Las columnas generadas (únicos entre no borrados como `code_active`, o `open_seller`) se declaran con
  `columnDefinition` y `insertable/updatable = false`.
- Las llaves foráneas hacia tablas de **otro módulo** no se declaran en las entidades: un módulo no importa entidades
  de otro.
- Las llaves foráneas hacia entidades con **borrado lógico** (`@SoftDelete`, todas las que heredan de `AuditInfo`)
  tampoco: Hibernate 7 no admite un `@ManyToOne` LAZY hacia ellas y no vale la pena cargarlas EAGER solo para tener
  la llave.
- Esas llaves (45) están en `config/schema/SchemaConstraints.java` y las crea **`SchemaFinisher`** al arrancar (ver
  abajo). No hay scripts SQL de esquema.
- Las relaciones padre-hijo que sí se usan (`AttributeOptionEntity.attribute`, `TemplateAttributeEntity.template`)
  quedan EAGER por la misma razón. En la práctica se cargan desde la colección del padre, así que no agregan consultas.

## Tarifario de envíos (logística)

Tablas `shipping_rate_cards`, `shipping_rates`, `shipping_rate_brackets`, `shipping_zones`, `shipping_zone_places` y
`shipping_quote_misses` (entidades de solo esquema en `shipping_logistics/tariff/.../schema`). Las crea Hibernate.

- `shipping_rate_cards.carrier_id` apunta a `carriers` sin FK declarada: `carriers` tiene borrado lógico (ver arriba).
- `shipping_zone_places.place_key` es único por empresa: un lugar del catálogo pertenece a una sola zona.
- Las franjas de peso deben ser continuas desde 0 kg; eso lo valida la aplicación (una CHECK no puede verlo entre filas).
- `storefront_shipping_rates` quedó como histórico: la tienda en línea cotiza con el tarifario. Se puede borrar cuando
  ya no haga falta.
- `shipping_costs` y `delivery_estimates` quedan como historial de solo lectura (el cálculo por dirección escrita se retiró).

## Arranque

| Ambiente | `ddl-auto` | Datos de referencia |
|---|---|---|
| Desarrollo (`application.properties`) | `update` | `data.sql` en cada arranque (idempotente) |
| Producción (`application-prod.yml`) | `validate` | `data.sql` en cada arranque (idempotente) |

`data.sql` carga solo lo que falte: unidades de medida, medios de pago, tarifas de impuesto, configuración y
numeración de facturación de pruebas, bodega, tienda y caja principales, catálogos de devoluciones, consecutivo de
órdenes y entidades bancarias (Bre-B). Nunca modifica ni borra datos.

## Lo que Hibernate no hace: `SchemaFinisher`

`update` crea tablas, columnas, índices, únicos y llaves declaradas, pero no crea las llaves que no están en las
entidades, no agrega CHECK a una tabla que ya existe y no cambia la nulabilidad de una columna. Eso lo completa
`config/schema/SchemaFinisher` al arrancar, después de Hibernate y de `data.sql`:

1. **Llaves foráneas** de `SchemaConstraints` que falten. Las busca por columna y tabla destino (no por nombre), así
   que no duplica una que Hibernate ya creó. Si hay filas huérfanas no la crea: lo dice en el log con cuántas son.
2. **CHECK** de las entidades (`@Table(check = @CheckConstraint(...))`): agrega las que falten y, si cambió la lista
   de valores (p. ej. un tipo nuevo en un enum), la reemplaza. Si hay filas que no cumplen la nueva, deja la anterior
   y lo avisa. En MySQL 5.7 (sin CHECK) se omiten.
3. **Columnas que pasaron a admitir NULL** (`SchemaConstraints.NULLABLE`), conservando el valor por defecto.

Solo agrega o amplía; nunca borra tablas, columnas ni datos, y un error no detiene el arranque. Corre en cada
arranque y si no hay nada que hacer lo dice en una línea.

| `ddl-auto` | Qué hace |
|---|---|
| `update` / `create` (desarrollo) | aplica los cambios |
| `validate` / `none` (producción) | solo informa en el log lo pendiente, con el SQL |

`essenza.schema.finisher.mode=apply|report|off` lo fuerza.

**Al agregar algo:** una llave que la entidad no puede declarar va en `SchemaConstraints`; una CHECK, en la entidad;
una columna que pasa a opcional, en `nullable = true` de la entidad **y** en `SchemaConstraints.NULLABLE`. Un cambio de
tipo de columna sigue siendo a mano.

## Producción

1. Genera el script desde las entidades: `./mvnw spring-boot:run -Dspring-boot.run.profiles=schema`
   (escribe `target/schema-create.sql` sin tocar la base; detén la aplicación cuando aparezca).
2. Revísalo y aplícalo (base nueva) o aplica solo las diferencias (base existente).
3. Producción arranca con `validate`: si falta algo, la aplicación no inicia y dice qué. `SchemaFinisher` informa en el
   log las llaves, CHECK y columnas opcionales pendientes con su SQL; aplícalo igual que el script.

## Base que venía con Flyway

Flyway está apagado. `db/migration` queda como historial **solo de datos**: los `INSERT` y `UPDATE` con que las
migraciones movieron y cargaron datos (sin `CREATE TABLE`, `ALTER TABLE`, índices ni llaves). Los catálogos que necesita
una base nueva están en `data.sql`.

Si tu base ya tenía aplicadas V2–V23, `SchemaFinisher` amplía las CHECK y la columna `invoices.order_id` al arrancar.
La tabla `flyway_schema_history` ya no se usa.
