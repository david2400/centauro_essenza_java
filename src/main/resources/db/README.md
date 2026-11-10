# Esquema de la base de datos

**JPA es la fuente del esquema.** Hibernate crea y actualiza las tablas desde las entidades:

- Entidades de los módulos (`*/infrastructure/outbound/persistence/...`), usadas también por los repositorios JPA.
- Entidades de **solo esquema** (`*/infrastructure/outbound/persistence/schema/*Entity`): tablas que se leen y
  escriben con JDBC (marketplace, compras, costo promedio, pagos agrupados, clientes, etc.). Solo existen para que
  Hibernate cree la tabla con sus columnas, únicos, índices, CHECK y llaves foráneas; no se usan en consultas.
- Las columnas generadas (únicos entre no borrados como `code_active`, o `open_seller`) se declaran con
  `columnDefinition` y `insertable/updatable = false`.
- Las llaves foráneas hacia tablas de **otro módulo** no se declaran: un módulo no importa entidades de otro.
- Las llaves foráneas hacia entidades con **borrado lógico** (`@SoftDelete`, todas las que heredan de `AuditInfo`)
  tampoco se declaran: Hibernate 7 no admite un `@ManyToOne` LAZY hacia ellas y no vale la pena cargarlas EAGER
  solo para tener la llave. Las bases que vienen de las migraciones ya las tienen; en una base nueva, ejecuta una
  vez `db/fk-borrado-logico.sql` después del primer arranque (es idempotente).
- Las relaciones padre-hijo que sí se usan (`AttributeOptionEntity.attribute`, `TemplateAttributeEntity.template`)
  quedan EAGER por la misma razón. En la práctica se cargan desde la colección del padre, así que no agregan consultas.

## Arranque

| Ambiente | `ddl-auto` | Datos de referencia |
|---|---|---|
| Desarrollo (`application.properties`) | `update` | `data.sql` en cada arranque (idempotente) |
| Producción (`application-prod.yml`) | `validate` | `data.sql` en cada arranque (idempotente) |

`data.sql` carga solo lo que falte: unidades de medida, medios de pago, tarifas de impuesto, configuración y
numeración de facturación de pruebas, bodega, tienda y caja principales, catálogos de devoluciones, consecutivo de
órdenes y entidades bancarias (Bre-B). Nunca modifica ni borra datos.

## Limitaciones de `update` (importante)

`update` crea tablas, columnas, índices, únicos y llaves que falten, pero **no cambia** el tipo, la nulabilidad ni
el valor por defecto de una columna existente, ni las restricciones CHECK. Si cambias algo así en una entidad,
aplica el cambio a mano en las bases que ya existen (desarrollo y producción).

## Producción

1. Genera el script desde las entidades: `./mvnw spring-boot:run -Dspring-boot.run.profiles=schema`
   (escribe `target/schema-create.sql` sin tocar la base; detén la aplicación cuando aparezca).
2. Revísalo y aplícalo (base nueva) o aplica solo las diferencias (base existente).
3. Producción arranca con `validate`: si falta algo, la aplicación no inicia y dice qué.

## Base que venía con Flyway

Flyway está apagado. Las migraciones `db/migration/V2–V33` quedan como historial. Si tu base ya tenía aplicadas
V2–V23, ejecuta una vez `db/transicion-jpa.sql` (cambios de V24–V33 sobre tablas existentes que `update` no hace).
Luego arranca la aplicación: Hibernate crea las tablas nuevas y `data.sql` carga los catálogos.
