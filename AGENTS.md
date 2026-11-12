# draco (centauro_essenza_java) — guía para personas y agentes

API de Essenza: Spring Boot 4 + JPA (MySQL/MariaDB) + Spring Security (tokens de access_control) + MapStruct + Lombok.
Arquitectura hexagonal por módulo: `modules/<modulo>/{domain, application/{dto,input,output,services}, infrastructure/{inbound,outbound}}`.

## Comandos

```bash
./mvnw -q compile            # compilar
./mvnw test                  # tests (Mockito/JUnit; los *IT necesitan BD)
./mvnw spring-boot:run       # local (perfil por defecto); secretos en config/local-secrets.properties
```

## Reglas

- **Capas**: `domain` no depende de Spring ni de JPA. `application/services` implementa los casos de uso de
  `application/input` y usa los puertos de `application/output`. Los adaptadores (`infrastructure/outbound`)
  implementan esos puertos; los controladores (`infrastructure/inbound/rest`) solo llaman casos de uso.
- **Módulos**: un módulo no usa entidades ni repositorios de otro; se comunican por casos de uso, puertos o eventos.
- **Autorización** en el backend, endpoint por endpoint (`AuthorizationRules`, capacidades y menús de
  access_control). No se confía en ids de empresa o vendedor que mande el cliente.
- **No modificar** `SpringSecurityConfig` ni `RouteRules.PUBLIC` sin una decisión explícita.
- **Dinero e inventario**: operaciones en una transacción, con bloqueo del registro (`lock*`), idempotentes
  cuando las llama un tercero (webhooks: firma + bitácora con clave única).
- **Esquema**: hoy lo genera JPA (`ddl-auto`) y `config/schema/SchemaFinisher` agrega FK, CHECK y relaja NOT NULL
  (`SchemaConstraints`). Para producción: línea base de Flyway y `ddl-auto=validate`. Cualquier columna que pase a
  ser opcional se agrega a `SchemaConstraints.NULLABLE`.
- **Errores**: `GlobalExceptionHandler` responde `ApiErrorResponse` (`message`, `code`, `fieldErrors`, `requestId`).
  Las reglas de negocio lanzan excepciones del dominio con mensajes en español para el usuario final.
- **Listas**: los listados que crecen sin límite tienen `/search` paginado; los hijos de un registro se filtran
  por el padre (`?orderId=`, `?dispatchProductId=`, `?orderDevolutionId=`), nunca se trae la tabla completa.

## Observabilidad

- `RequestIdFilter` toma o genera `X-Request-Id`, lo pone en el MDC (`requestId`), en cada línea de log y en
  la respuesta. Para seguir un error del panel, busca su "ref" en los logs.
- `/actuator/health` (con BD) e `info`; en prod además `metrics`. Requieren autenticación mientras no se decida
  hacer pública la salud (`RouteRules.PUBLIC`).
- Jobs programados en `infrastructure/inbound/scheduling` (vencimiento de reservas, conciliación de pagos en
  línea, liquidaciones, comisiones); cada uno se configura en `application.properties`.
