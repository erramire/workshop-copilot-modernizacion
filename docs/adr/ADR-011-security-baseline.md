# ADR-011: Línea base de seguridad

| Campo | Valor |
| --- | --- |
| Estado | Aceptado |
| Fecha | 2026-10-04 |
| Relacionado | [Sección G de blockers.md](../blockers.md#g-seguridad), [ADR-009](ADR-009-web-layer-thymeleaf.md), [ADR-010](ADR-010-welcome-notification.md), [ADR-012](ADR-012-config-observability.md), [riesgos](../risks.md) (R-08, R-14, R-18) |

## Contexto

- El legacy no tiene autenticación ni protección CSRF.
- Además tiene XSS latente, muestra mensajes de excepción al usuario, escribe PII en los logs, guarda secretos en archivos versionados, conecta por JDBC sin TLS, confía en `X-Forwarded-For` sin validarlo y tiene un catch-all en `/`.
- La autenticación completa queda fuera del alcance del taller.

## Decisión

- **Spring Security** con un `SecurityFilterChain` explícito:
  - `anyRequest().permitAll()`: el acceso sigue siendo anónimo, como en el legacy.
  - CSRF habilitado (es el default). `formLogin` y `httpBasic` deshabilitados. Se excluye `UserDetailsServiceAutoConfiguration` para que no se genere un usuario por defecto.
  - Headers:
    - Los defaults de Spring Security.
    - `Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'`.
    - `Referrer-Policy: same-origin`.
    - HSTS: Spring lo emite cuando la petición llega por HTTPS (ingress de ACA + `forward-headers-strategy`).
- **Entrada y salida:** Bean Validation en `StudentForm` y escapado de Thymeleaf ([ADR-009](ADR-009-web-layer-thymeleaf.md)).
- **Errores:**
  - `server.error.include-message`, `include-stacktrace` e `include-binding-errors` en `never`.
  - `GlobalExceptionHandler` (`@ControllerAdvice`) convierte `DataAccessException` y los errores inesperados en un mensaje genérico, y registra el detalle con el id de traza.
- **Logs:** a nivel INFO no se registran el email ni el nombre, solo `studentId`. En `prod` los logs son estructurados (ECS), lo que escapa los CRLF.
- **Secretos:** ninguno en el repositorio, en el `Dockerfile` ni en `application*.yml`. Solo llegan como variables de entorno, alimentadas por secretos de ACA o referencias a Key Vault.
- **Proxy:** no hay filtro propio de IP. Se usa `server.forward-headers-strategy=framework`, porque el ingress de ACA es el único proxy confiable.
- **Base de datos:** TLS obligatorio con MySQL ([ADR-005](ADR-005-hibernate-strategy.md)). La consola de H2 queda deshabilitada.
- **Actuator:** solo se expone `health`, con `show-details=never`.
- **Autenticación de usuarios:** queda fuera de alcance y se registra como trabajo futuro (Entra ID con `spring-boot-starter-oauth2-client`). Es **prerrequisito** para activar SMTP o para exponer datos reales (R-08).

## Cómo se resuelve cada hallazgo

| Hallazgo | Resolución |
| --- | --- |
| S-01 Secretos versionados | Variables de entorno y secretos de ACA; Dockerfile sin `ENV` sensibles |
| S-02 Sin autenticación | **Pendiente** (trabajo futuro) |
| S-03 Email a cualquier dirección | **Mitigado:** modo `log` por defecto ([ADR-010](ADR-010-welcome-notification.md)); `smtp` exige autenticación previa |
| S-04 JDBC sin TLS | `sslMode=VERIFY_IDENTITY` |
| S-05 Dependencias con CVEs | [ADR-007](ADR-007-cve-remediation.md) |
| S-06 Sin CSRF | CSRF de Spring Security |
| S-07 XSS latente | `th:text` de Thymeleaf |
| S-08 Excepciones expuestas al usuario | Mensajes genéricos y `server.error.*=never` |
| S-09 PII en logs | Solo ids; logs estructurados |
| S-10 Sin validación en servidor | Bean Validation |
| S-11 JSP accesibles directamente | Plantillas en `templates/`, que no se sirven directamente |
| S-12 Confianza en headers de IP | Filtro eliminado; `forward-headers-strategy` |
| S-13 Catch-all en `/` | 404 para rutas desconocidas |

## Alternativas consideradas

| Opción | Pros | Contras | Resultado |
| --- | --- | --- | --- |
| Sin Spring Security (CSRF a mano) | Menos dependencias | Reinventa controles ya probados | Descartada |
| Autenticación con Entra ID desde ya | Cierra S-02 | Requiere registrar la app en un tenant; fuera del alcance del taller | Pospuesta |

## Consecuencias

- **Positivas:**
  - Cierra 11 de los 13 hallazgos y mitiga S-03.
  - Deja la base lista para agregar autenticación.
- **Negativas:** el token CSRF y los mensajes flash viven en la sesión HTTP. Con varias réplicas y sin afinidad de sesión, los POST pueden fallar (R-14).

## Validación

- Tests con MockMvc:
  - Un POST sin token devuelve 403.
  - Están presentes los headers CSP, `X-Content-Type-Options` y `X-Frame-Options`.
  - Un error de base de datos muestra una página genérica, sin detalle técnico.
- `grep -riE "password|secret" src/student-web-app --include=*.yml --include=Dockerfile` no encuentra valores literales.
