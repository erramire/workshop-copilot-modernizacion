# ADR-010: Configuración, secretos y observabilidad

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisión 10 y decisiones por defecto del planning, aceptadas en el chat)

## Contexto
- En el legacy, la configuración está repartida entre `server.xml` y `server.env` de Liberty, el `Dockerfile` y `docker-compose.yml`, y lleva secretos escritos en esos archivos (SEC-01).
- log4j 1 escribe en un archivo local, `/logs/applog/...` (FUN-05). No hay endpoint de health.
- El Bicep ya inyecta `SPRING_PROFILES_ACTIVE=prod` y `APPLICATIONINSIGHTS_CONNECTION_STRING` en la Container App Java, que hoy no tiene probes. No crea Key Vault.
- El devcontainer del taller y el facilitador comprueban `/health` en el puerto 8080.

## Opciones consideradas
1. **`application.yml` con valores por defecto seguros, variables de entorno y secretos de Container Apps.** Key Vault queda documentado para producción.
2. **Añadir Key Vault al Bicep ya en el taller.**
   - En contra: más infraestructura, cuando en el taller no hay ningún secreto obligatorio.
3. **Azure App Configuration.**
   - En contra: sobredimensionado para tan pocas propiedades.

## Decisión
Opción 1.

### Configuración
- Spring Boot vincula automáticamente las variables de entorno con las propiedades (relaxed binding). `application.yml` no contiene ningún secreto.
- Perfiles:
  - Por defecto: entorno local y taller.
  - `prod`: lo fija el Bicep. Solo activa `Secure` en la cookie de sesión y no exige ninguna variable adicional.

| Propiedad | Variable de entorno | Valor por defecto | Notas |
| --- | --- | --- | --- |
| `server.port` | `SERVER_PORT` | `8080` | |
| `server.forward-headers-strategy` | — | `native` | La app está detrás del ingress de Container Apps |
| `server.shutdown` | — | `graceful` | |
| `server.error.include-message` e `include-stacktrace` | — | `never` | [ADR-009](ADR-009-seguridad.md) |
| `server.servlet.session.cookie.same-site` | — | `lax` | `secure: true` en el perfil `prod` |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:h2:mem:studentdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1` | [ADR-005](ADR-005-base-de-datos.md) |
| `spring.datasource.username` y `password` | `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` | `sa` y vacío | Si se usa MySQL, la contraseña se pasa como secreto |
| `spring.jpa.hibernate.ddl-auto` | — | `validate` | El esquema lo gestiona Flyway |
| `spring.jpa.open-in-view` | — | `false` | |
| `spring.flyway.baseline-on-migrate` | `SPRING_FLYWAY_BASELINE_ON_MIGRATE` | `false` | `true` solo para adoptar una BD legacy |
| `app.mail.welcome.enabled` | `APP_MAIL_WELCOME_ENABLED` | `false` | [ADR-011](ADR-011-notificacion-email.md) |
| `app.mail.welcome.from` | `APP_MAIL_WELCOME_FROM` | `noreply@example.com` | |
| `spring.mail.host`, `port`, `username` y `password` | `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME` y `SPRING_MAIL_PASSWORD` | Vacío, `587`, vacío y vacío | La contraseña se pasa como secreto |
| `management.endpoints.web.base-path` | — | `/` | Hace que el health quede en `/health` |
| `management.endpoints.web.exposure.include` | — | `health` | |
| `management.endpoint.health.probes.enabled` | — | `true` | Expone `/health/liveness` y `/health/readiness` |
| `management.endpoint.health.show-details` | — | `never` | |
| — | `APPLICATIONINSIGHTS_CONNECTION_STRING` | Sin valor | La inyecta el Bicep y activa el agente |
| — | `SPRING_PROFILES_ACTIVE` | Sin valor (`prod` en Azure) | |

### Secretos
- En el taller no hay ningún secreto obligatorio: la base de datos es H2 y el email está desactivado.
- Si se activa MySQL o el email, las contraseñas se guardan como secretos de la Container App y llegan a la app como variables de entorno mediante `secretRef`.
- En producción: referencias a Key Vault resueltas con la Managed Identity que ya existe (Fase 4, fuera de alcance).

### Observabilidad
- **Logs:** SLF4J con Logback (lo que trae Spring Boot por defecto), escritos en stdout. Container Apps los envía a Log Analytics. No hay appenders de archivo.
- **Health:** Actuator en `/health`. Container Apps puede usar `/health/liveness` y `/health/readiness` como probes; configurarlas en el Bicep es tarea de Fase 4.
- **Application Insights:** agente Java 3.7.9, copiado en el build ([ADR-004](ADR-004-build-empaquetado.md)). Solo se adjunta (`-javaagent`) si existe `APPLICATIONINSIGHTS_CONNECTION_STRING`, así que en local la imagen arranca sin él. Opcionalmente, un `applicationinsights.json` puede descartar la telemetría de `/health*`.

## Consecuencias
- **Positivas:**
  - La misma imagen funciona en local y en Azure sin cambiar nada.
  - No hay secretos en Git ni en la imagen.
  - La telemetría funciona sin tocar el código.
- **Negativas:** el agente añade tiempo de arranque.
- **Riesgos a monitorear:** R-10 en [risks.md](../risks.md).

## Referencias
- [blockers.md](../blockers.md) (SEC-01, FUN-05, PLT-05) e `infra/main.bicep` (Container App `petclinicApp`)
- https://learn.microsoft.com/azure/azure-monitor/app/java-standalone-config
