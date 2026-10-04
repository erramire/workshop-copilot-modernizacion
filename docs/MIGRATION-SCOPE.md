# Alcance de la migración: Student Web App

> **Fase 2** · Fecha: 2026-10-04 · Es un insumo obligatorio de `@spring-legacy-migration`.
>
> - Arquitectura: [ARQUITECTURA-TARGET.md](ARQUITECTURA-TARGET.md)
> - Plan: [migration-plan.md](migration-plan.md)

## Objetivo

Entregar `src/student-web-app/`, que debe incluir:

- Un JAR de Spring Boot 3.5.16 sobre Java 21 con las 3 features del legacy.
- Una imagen Docker que responde en el puerto 8080.
- Tests que verifican la [matriz de paridad](#matriz-de-paridad).

## En alcance

| Ítem | Referencia |
| --- | --- |
| F-01 Listado de perfiles | [feature](features/01-listado-perfiles-estudiantes.md), [ADR-009](adr/ADR-009-web-layer-thymeleaf.md) |
| F-02 Alta de perfil | [feature](features/02-alta-perfil-estudiante.md), [ADR-009](adr/ADR-009-web-layer-thymeleaf.md) |
| F-03 Notificación de bienvenida (modo `log` por defecto, `smtp` opcional) | [feature](features/03-email-bienvenida.md), [ADR-010](adr/ADR-010-welcome-notification.md) |
| Build con Maven y Maven Wrapper | [ADR-003](adr/ADR-003-namespace-strategy.md), [ADR-006](adr/ADR-006-packaging.md) |
| Cambio de namespace a `jakarta.*` con OpenRewrite | [ADR-003](adr/ADR-003-namespace-strategy.md) |
| Persistencia con JPA y Flyway, sobre H2 y MySQL 8.4 | [ADR-005](adr/ADR-005-hibernate-strategy.md) |
| Línea base de seguridad | [ADR-011](adr/ADR-011-security-baseline.md) |
| Configuración, health y Application Insights | [ADR-012](adr/ADR-012-config-observability.md) |
| Dockerfile multi-stage | [ADR-006](adr/ADR-006-packaging.md) |
| Tests por capa y de paridad | [ADR-008](adr/ADR-008-test-strategy.md) |
| Remediación de CVEs | [ADR-007](adr/ADR-007-cve-remediation.md) |

## Fuera de alcance

| Ítem | Motivo o responsable |
| --- | --- |
| Autenticación y autorización de usuarios | Trabajo futuro ([ADR-011](adr/ADR-011-security-baseline.md)); es prerrequisito para usar SMTP real |
| API REST o JSON | No hay consumidores conocidos ([ADR-009](adr/ADR-009-web-layer-thymeleaf.md)) |
| Aprovisionar en Azure la base de datos, el email, los probes o Key Vault | Fase 4 (`@azure-architect`) |
| Migrar datos productivos | El taller no tiene datos productivos; para adoptar una base existente alcanza con el baseline de Flyway |
| Upgrade a Spring Boot 4.x | Después del taller ([ADR-002](adr/ADR-002-spring-boot-version.md)) |
| Cualquier cambio en `legacy/` | `legacy/` es de solo lectura |
| Los otros samples de `legacy/java/` y el Lab 01 (.NET) | Son otros flujos |
| CI/CD | El taller no lo pide |
| Corregir el material de los labs | La lista de ajustes está en [migration-plan.md](migration-plan.md#ajustes-requeridos-al-material-del-taller) |

## Matriz de paridad

Cada fila necesita al menos un test ([ADR-008](adr/ADR-008-test-strategy.md)) y debe quedar registrada en `migration/parity-notes.md`.

| ID | Comportamiento legacy | Comportamiento target | Tipo |
| --- | --- | --- | --- |
| P-01 | El listado muestra todos los perfiles con ID, Name, Email y Major | Igual, en `/students` | Se conserva |
| P-02 | El orden de las filas no está definido | Se ordena por `id` ascendente | Cambio intencional |
| P-03 | Los valores se escapan en el HTML | Igual (`th:text` de Thymeleaf) | Se conserva |
| P-04 | Con la tabla vacía se muestra "No student profiles found." | Igual | Se conserva |
| P-05 | Un error de BD muestra el mensaje de la excepción (`/`), una lista vacía (`/app/`) o HTML parcial con 500 (`/studentProfileList`) | Página de error genérica (HTTP 500) sin detalle técnico; el detalle va a los logs | Cambio intencional |
| P-06 | `/studentProfileList` agrega un JSON con la lista | Se elimina | Cambio intencional |
| P-07 | Los campos obligatorios se validan solo en el navegador | Validación en el servidor: obligatorios, email válido y máximo 255 caracteres, con errores por campo | Cambio intencional |
| P-08 | Se permiten emails duplicados | Igual (sin `UNIQUE`) | Se conserva |
| P-09 | Un alta exitosa devuelve HTML inline (servlet) o hace PRG con flash (Spring) | PRG a `/students` con el mensaje "Student {name} has been added successfully!" | Unificado |
| P-10 | En el servlet, el error de alta no se muestra (bug `errorMsg`) | Se muestra un mensaje genérico | Bug corregido |
| P-11 | El email de bienvenida solo sale por el servlet, después del commit; si falla, el alta no se revierte | Sale en todas las altas, después del commit; si falla, el alta no se revierte; por defecto en modo `log` | Unificado (validar con negocio) |
| P-12 | Asunto y cuerpo del email | Idénticos | Se conserva |
| P-13 | URLs `/`, `/addStudent`, `/studentProfileList` y `/app/*` | Los GET devuelven 301 a las URLs nuevas; los POST legacy no se soportan | Cambio intencional |
| P-14 | Cualquier URL desconocida muestra la portada con 200 | 404 genérico | Cambio intencional |
| P-15 | Acceso anónimo | Igual | Se conserva |
| P-16 | Un POST sin token CSRF se acepta | Responde 403 | Cambio intencional |
| P-17 | El id generado no se muestra al usuario | Igual | Se conserva |
| P-18 | Puertos 9080/9443, context-root `/` | Puerto 8080, context-root `/` | Cambio intencional (infraestructura) |
| P-19 | Los logs incluyen PII y se escriben a un archivo | Logs sin PII, a stdout | Cambio intencional |

## Supuestos

- Nadie consume el JSON incrustado de `/studentProfileList`.
- Negocio acepta los cambios intencionales; P-11 es el que más hay que validar.
- En el taller se acepta perder los datos cuando la app escala a cero.

## Definición de terminado (Fase 3)

- [ ] `./mvnw -B verify` pasa, incluidos Testcontainers y el enforcer.
- [ ] No quedan imports `javax.(servlet|mail|persistence|validation|annotation)` en `src/main/java`.
- [ ] No quedan XML de Spring, `web.xml`, JSP, iBATIS, log4j 1 ni Jackson 1.
- [ ] Cada fila P-xx tiene al menos un test y está registrada en `migration/parity-notes.md`.
- [ ] Con `docker build` + `docker run`, `/actuator/health` responde UP en el puerto 8080.
- [ ] `./mvnw -Psecurity-scan verify` no reporta hallazgos de 7.0 o más sin justificar.
- [ ] `git -C legacy/java status --porcelain` no muestra cambios.
