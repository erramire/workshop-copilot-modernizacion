# ADR-012: Configuración, health checks y observabilidad

| Campo | Valor |
| --- | --- |
| Estado | Aceptado |
| Fecha | 2026-10-04 |
| Relacionado | [B-15](../blockers.md#b-15), [ADR-006](ADR-006-packaging.md), [ADR-011](ADR-011-security-baseline.md), `infra/main.bicep`, [riesgos](../risks.md) (R-07, R-09) |

## Contexto

- En el legacy, la configuración está repartida entre `server-docker.xml`, `server-docker.env`, el `Dockerfile` y `docker-compose.yml`, e incluye secretos.
- log4j escribe a un archivo en una ruta absoluta.
- No hay health check.
- Para la app Java, el Bicep ya define `SPRING_PROFILES_ACTIVE=prod` y `APPLICATIONINSIGHTS_CONNECTION_STRING`, pero no define probes.

## Decisión

### Configuración (12-factor)

- `application.yml` trae defaults que funcionan sin ninguna variable: H2 y notificación en modo `log`, para local y para el taller.
- `application-prod.yml` solo agrega endurecimiento operativo: logs ECS y detalles de health ocultos.
- Cualquier valor que dependa del entorno se sobrescribe con variables de entorno (relaxed binding: `SPRING_DATASOURCE_URL`, `APP_NOTIFICATION_WELCOME_EMAIL_MODE`, etc.). La tabla completa está en [ARQUITECTURA-TARGET.md](../ARQUITECTURA-TARGET.md#configuración).
- No se usa Spring Cloud Config ni App Configuration: no hacen falta para una app de este tamaño.

### Logging

- SLF4J + Logback (el default de Spring Boot), **solo a stdout**, sin appenders de archivo.
- En `prod`, `logging.structured.format.console=ecs` (JSON) para Log Analytics.
- Los niveles se ajustan por variable de entorno (`LOGGING_LEVEL_ORG_SAMPLE_AZURE_STUDENT`).

### Health

- Spring Boot Actuator, exponiendo solo `health`, con `management.endpoint.health.probes.enabled=true`.
- Endpoints:
  - `/actuator/health/liveness`: estado de la app.
  - `/actuator/health/readiness`: incluye la base de datos.
- Requisito para la Fase 4: probes de liveness, readiness y startup en ACA, con margen para el arranque en frío.

### Application Insights

- Dependencia `com.microsoft.azure:applicationinsights-runtime-attach` (3.7.10 o superior), con `ApplicationInsights.attach()` como primera línea de `main`.
- Lee `APPLICATIONINSIGHTS_CONNECTION_STRING`, que ya define el Bicep. Si no está (por ejemplo, en local), no envía telemetría.
- `APPLICATIONINSIGHTS_ROLE_NAME=student-web-app` identifica el servicio en Application Insights.
- Recolecta automáticamente requests, dependencias JDBC, logs de Logback y métricas de Micrometer.
- El entrypoint lleva `-XX:+EnableDynamicAgentLoading` para evitar la advertencia de JEP 451 ([ADR-006](ADR-006-packaging.md)).

## Alternativas consideradas

| Opción | Pros | Contras | Resultado |
| --- | --- | --- | --- |
| `-javaagent` descargado en el Dockerfile | No carga el agente dinámicamente | El artefacto queda fuera de Maven y del escaneo de CVEs | Descartada |
| OpenTelemetry + exportador de Azure Monitor | Estándar abierto | Requiere más configuración | Descartada para el taller |
| **Runtime attach** | Versionado en Maven, sin tocar la imagen | Advertencia de JEP 451 (mitigada con el flag) | **Elegida** |

## Consecuencias

- **Positivas:**
  - La misma imagen corre en local, en el taller y en producción; solo cambian las variables.
  - Logs y trazas quedan correlacionados en Application Insights y Log Analytics.
- **Negativas:** el agente alarga el arranque (R-07).

## Validación

- `curl localhost:8080/actuator/health/liveness` y `curl localhost:8080/actuator/health/readiness` devuelven `UP`.
- Con `SPRING_PROFILES_ACTIVE=prod`, los logs salen en JSON.
- En Azure, los requests aparecen en Application Insights con el role name `student-web-app`.
