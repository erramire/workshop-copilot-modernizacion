# Registro de riesgos: migración de Student Web App

> **Fase 2** · Fecha: 2026-10-04
>
> - P = probabilidad, I = impacto. Ambos se miden como Alta, Media o Baja.
> - Revisar este registro al cerrar cada paso del [plan](migration-plan.md).

## Resumen

| Nivel | Riesgos |
| --- | --- |
| Críticos (P Alta, I Alta) | R-01, R-15 |
| Altos | R-02, R-04, R-07, R-08, R-10, R-12 |
| Medios y bajos | El resto |

## Registro

| ID | Riesgo | P | I | Mitigación | Referencia |
| --- | --- | --- | --- | --- | --- |
| R-01 | Spring Boot 3.5 no recibe parches OSS desde el 30-jun-2026; los CVEs futuros no tendrán corrección pública | Alta | Alta | Contratar soporte comercial o subir a Spring Boot 4.x después del taller; correr `security-scan` en CI | [ADR-002](adr/ADR-002-spring-boot-version.md), [ADR-007](adr/ADR-007-cve-remediation.md) |
| R-02 | Regresiones funcionales, porque el legacy no tiene tests | Media | Alta | Tests de caracterización a partir de la matriz de paridad (P-01 a P-19); revisión manual de las 3 features | [ADR-008](adr/ADR-008-test-strategy.md), [MIGRATION-SCOPE.md](MIGRATION-SCOPE.md#matriz-de-paridad) |
| R-03 | No se puede ejecutar la línea base legacy en Codespaces (falta Ant) para comparar resultados | Alta | Media | Paridad basada en la especificación. Opcional: instalar Ant y levantar el `docker compose` del legacy | [B-05](blockers.md#b-05) |
| R-04 | Con H2 en memoria en ACA, los datos se pierden al escalar a cero y difieren entre réplicas | Alta | Media | `maxReplicas: 1`; avisar que en el taller los datos no persisten; usar MySQL 8.4 en entornos reales | [ADR-005](adr/ADR-005-hibernate-strategy.md) |
| R-05 | Los cambios intencionales (URLs, 404, CSRF, validación, email en todas las altas) sorprenden a usuarios o integraciones | Media | Media | Redirecciones 301 desde las URLs legacy; publicar la matriz de paridad; validar P-11 con negocio | [ADR-009](adr/ADR-009-web-layer-thymeleaf.md), [ADR-010](adr/ADR-010-welcome-notification.md) |
| R-06 | OpenRewrite no cubre todos los casos (XML, strings) | Baja | Baja | Greps de validación; `web.xml` y las JSP se eliminan de todas formas | [ADR-003](adr/ADR-003-namespace-strategy.md) |
| R-07 | Arranque en frío lento con `minReplicas: 0` (Spring, Hibernate, Flyway y el agente de Application Insights) | Alta | Media | Startup probe con margen de 120 s o más; avisar a los participantes. Opcional: `minReplicas: 1` o CDS | [ADR-006](adr/ADR-006-packaging.md), [ADR-012](adr/ADR-012-config-observability.md) |
| R-08 | Activar SMTP sin autenticación permite enviar emails a cualquier dirección | Media | Alta | Modo `log` por defecto; exigir autenticación o rate limiting antes de usar `smtp` | [ADR-010](adr/ADR-010-welcome-notification.md), [ADR-011](adr/ADR-011-security-baseline.md) |
| R-09 | La carga dinámica del agente de Application Insights genera una advertencia o queda bloqueada (JEP 451) | Media | Baja | `-XX:+EnableDynamicAgentLoading`; como alternativa, `-javaagent` | [ADR-012](adr/ADR-012-config-observability.md) |
| R-10 | El material del taller tiene inconsistencias (rutas `legacy/java`, PetClinic, push a Azure-Samples, ADR-004 in-place) que confunden a participantes y agentes | Alta | Media | Aplicar los [ajustes al material](migration-plan.md#ajustes-requeridos-al-material-del-taller) antes de dictar el taller | [migration-plan.md](migration-plan.md) |
| R-11 | H2 y MySQL 8.4 se comportan distinto (dialecto, `sql_mode`, autenticación `caching_sha2_password`) | Media | Media | Tests de repositorio con Testcontainers MySQL 8.4; Connector/J 9.x | [ADR-005](adr/ADR-005-hibernate-strategy.md), [ADR-008](adr/ADR-008-test-strategy.md) |
| R-12 | Al adoptar una base existente, Flyway intenta crear una tabla que ya existe | Baja | Alta | `baseline-on-migrate=true` con `baseline-version=1`; probar primero sobre una copia | [ADR-005](adr/ADR-005-hibernate-strategy.md) |
| R-13 | La CSP estricta bloquea los estilos inline heredados de las JSP | Media | Baja | Pasar el CSS a `static/css/app.css`; test de headers y revisión visual | [ADR-009](adr/ADR-009-web-layer-thymeleaf.md), [ADR-011](adr/ADR-011-security-baseline.md) |
| R-14 | El token CSRF y los mensajes flash dependen de la sesión HTTP: con varias réplicas sin afinidad, los POST fallan con 403 o se pierden mensajes | Media | Media | `maxReplicas: 1` en el taller; sticky sessions de ACA o sesión externa si se escala | [ADR-011](adr/ADR-011-security-baseline.md) |
| R-15 | El `.gitignore` de la raíz no funciona (pone varios patrones por línea), así que `target/`, `.env`, `*.pem` y `*.key` no se ignoran y pueden terminar en un commit | Alta | Alta | Corregirlo (un patrón por línea) antes de la Fase 3 | Fuera de la app |
| R-16 | Los artefactos chocan con los del Lab 01 (.NET) en `docs/ARQUITECTURA-TARGET.md`, `docs/features/` y `docs/inventory/` | Media | Media | Ejecutar los labs en orden y respaldar, o separar por carpeta (`docs/java/`, `docs/dotnet/`) | Material del taller |
| R-17 | El agente de migración entra en un bucle de errores, o se cierra la sesión de chat a mitad del proceso | Media | Media | Pasos pequeños con un commit cada uno; retomar desde `migration/migration-log.md` | [migration-plan.md](migration-plan.md) |
| R-18 | Si Spring Security queda configurado a medias, sus defaults (usuario generado, página de login) cambian la experiencia de uso | Baja | Media | `SecurityFilterChain` explícito; excluir `UserDetailsServiceAutoConfiguration`; tests | [ADR-011](adr/ADR-011-security-baseline.md) |

## Riesgos aceptados

| ID | Motivo de la aceptación |
| --- | --- |
| R-04 | Es suficiente para el taller. En entornos reales se usa MySQL |
| R-06 | El impacto es bajo y está cubierto por la validación |
| R-09 | Está mitigado con un flag de la JVM |
