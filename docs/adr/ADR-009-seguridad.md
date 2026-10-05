# ADR-009: Seguridad de la aplicación

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisiones 9 y 12 del planning, aceptadas en el chat)

## Contexto
- El legacy tiene estos problemas de seguridad (SEC-01 a SEC-11 en [blockers.md](../blockers.md)):
  - No hay autenticación, autorización ni protección CSRF.
  - El servidor no valida la entrada.
  - Los mensajes de excepción llegan al usuario.
  - Los logs contienen datos personales.
  - El envío de email se puede usar como relay de spam.
  - Hay secretos escritos en archivos versionados.
- El planning decidió mantener la app sin login, como hoy, pero reforzada.
- Las reglas del taller prohíben secretos en el código.

## Opciones consideradas
1. **Sin login, como hoy, pero reforzada:** CSRF, validación, cabeceras, logs sin datos personales, errores genéricos y secretos fuera del código.
2. **Login con Microsoft Entra ID (OIDC) en toda la app.**
3. **Entra ID solo para las operaciones de escritura.**

## Decisión
Opción 1.

| Control | Implementación | Hallazgo |
| --- | --- | --- |
| Autorización | `SecurityFilterChain` de Spring Security con todas las rutas en `permitAll()`, sin `formLogin` ni `httpBasic`. Se excluye `UserDetailsServiceAutoConfiguration` para que no se genere ni se escriba en el log una contraseña por defecto | SEC-02 (riesgo aceptado) |
| CSRF | Activado (es el valor por defecto). Thymeleaf incluye el token en todos los formularios | SEC-04 |
| Cabeceras | Las que Spring Security añade por defecto: `X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control` y HSTS en HTTPS | SEC-11 |
| Validación | Bean Validation en `StudentForm`. `name` y `major`: `@NotBlank` y `@Size(max = 255)`. `email`: `@NotBlank`, `@Email` y `@Size(max = 255)`. Se recortan los espacios al inicio y al final | SEC-05 |
| Errores | `server.error.include-message=never` e `include-stacktrace=never`. El usuario solo ve mensajes genéricos ([ADR-007](ADR-007-capa-web.md)) | SEC-06 |
| Logs | Nunca se escriben nombre ni email; se registra el id. Logging parametrizado con SLF4J | SEC-07 |
| Email | Un único destinatario: `@Email` lo valida y `MimeMessageHelper` rechaza cadenas con varias direcciones. El envío está desactivado por defecto ([ADR-011](ADR-011-notificacion-email.md)) | SEC-03 |
| XSS | Escape automático de Thymeleaf ([ADR-008](ADR-008-frontend.md)) | SEC-09 |
| Secretos | Ninguno en el código, en `application.yml` ni en la imagen ([ADR-010](ADR-010-configuracion-observabilidad.md)) | SEC-01 |
| Cookies de sesión | `HttpOnly` y `SameSite=Lax`, más `Secure` en el perfil `prod` | — |
| Proxy | `server.forward-headers-strategy=native`, que sustituye al filtro muerto `CommonHttpServletFilter` | SEC-10 |
| Actuator | Solo se expone `health`, sin detalles | — |
| Dependencias | Versiones del BOM de Spring Boot más escaneo de CVEs ([ADR-004](ADR-004-build-empaquetado.md)) | SEC-08 |

Riesgos residuales que se aceptan para el taller (fuera de alcance):
- **Autenticación:** cualquiera puede ver los emails y crear registros. Antes de producción hace falta un ADR de autenticación con Microsoft Entra ID.
- **Rate limiting:** en producción debe aplicarse delante de la app, con Front Door o API Management.

## Consecuencias
- **Positivas:** quedan cerrados todos los hallazgos explotables del legacy, salvo la falta de login.
- **Negativas:** los clientes que hacían POST sin token CSRF (por ejemplo, el `curl` de la documentación del sample) recibirán un 403.
- **Riesgos a monitorear:** R-06, R-07 y R-08 en [risks.md](../risks.md).

## Referencias
- [blockers.md](../blockers.md), sección 4 (seguridad)
- [features/02-registro-estudiante.md](../features/02-registro-estudiante.md) y [features/03-notificacion-bienvenida.md](../features/03-notificacion-bienvenida.md)
