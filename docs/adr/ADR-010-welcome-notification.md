# ADR-010: Notificación de bienvenida desacoplada

| Campo | Valor |
| --- | --- |
| Estado | Aceptado (el comportamiento de P-11 está por confirmar con negocio) |
| Fecha | 2026-10-04 |
| Relacionado | [B-10](../blockers.md#b-10), [F-03](../features/03-email-bienvenida.md), [ADR-011](ADR-011-security-baseline.md), [riesgos](../risks.md) (R-08) |

## Contexto

- Hoy solo `POST /addStudent` (el servlet) envía el email. Lo hace de forma síncrona y después del commit. Si el envío falla, el alta se mantiene y el usuario ve un mensaje de éxito.
- El envío usa la sesión JNDI `mail/StudentMailSession`, con SMTP en `localhost:25` y credenciales en texto plano.
- El Bicep del taller no tiene servicio de email, y el puerto 25 de salida está bloqueado en la mayoría de las suscripciones de Azure.
- Como no hay autenticación, cualquiera podría usar el formulario para mandar emails a direcciones arbitrarias.

## Decisión

- Una interfaz (puerto) `WelcomeNotifier` con el método `void sendWelcome(StudentProfile student)`.
- `StudentService.register` publica `StudentRegisteredEvent`. `WelcomeNotificationListener` lo escucha con `@TransactionalEventListener(phase = AFTER_COMMIT)` y llama al notificador. Así, **se notifica en todas las altas**, y solo cuando el commit fue exitoso.
- El listener **captura y registra** cualquier excepción del notificador: el alta no se revierte y el usuario ve el mensaje de éxito, igual que en el legacy.
- El envío es **síncrono**, como en el legacy. Pasarlo a asíncrono (`@Async` o una cola) queda para más adelante.
- **Implementaciones**, seleccionadas con `app.notification.welcome-email.mode`:

| Modo | Clase | Qué hace | Cuándo usarla |
| --- | --- | --- | --- |
| `log` (default) | `LoggingWelcomeNotifier` | Registra `welcome notification for studentId={id}`, sin email ni nombre | Local, taller y ACA del taller |
| `smtp` | `SmtpWelcomeNotifier` | Envía con `JavaMailSender` (`spring-boot-starter-mail`, `jakarta.mail`), STARTTLS en el puerto 587, desde el remitente `app.notification.welcome-email.from` | Entornos con un proveedor de email configurado |

- El contenido es el mismo del legacy:
  - Asunto: `Welcome, {name}!`
  - Cuerpo: `Dear {name},\n\nYour student profile has been created successfully.\n\nRegards,\nAdmin`
- Las credenciales SMTP solo llegan por variables de entorno o secretos (`SPRING_MAIL_*`).
- El proveedor de producción (ACS Email o SendGrid) se decide en la Fase 4. Se puede agregar un `AcsWelcomeNotifier` con Managed Identity sin tocar el servicio.

## Alternativas consideradas

| Opción | Pros | Contras | Resultado |
| --- | --- | --- | --- |
| Enviar el email solo en un camino, como hoy | Paridad exacta | Mantiene la divergencia | Descartada |
| SDK de ACS Email con Managed Identity desde ya | Sin secretos | Requiere infraestructura que el taller no tiene | Pospuesta |
| Cola (Service Bus) + worker | Resiliente | Desproporcionado para el caso | Pospuesta |
| **Puerto + log por defecto + SMTP opcional** | Funciona sin infraestructura y se puede extender | — | **Elegida** |

## Consecuencias

- **Positivas:**
  - La app funciona en el taller sin servicio de email.
  - El riesgo de abuso queda mitigado por defecto.
- **Negativas:**
  - Cambia el comportamiento: el camino de Spring MVC ahora también notifica. Hay que validarlo con negocio.
  - Activar `smtp` en producción sin autenticación vuelve a abrir el riesgo de abuso (R-08). Antes de activarlo se necesita autenticación o rate limiting.

## Validación

- Tests de alta:
  - Tras un alta exitosa, el notificador se invoca una sola vez.
  - Si hay rollback, no se invoca.
  - Si el notificador lanza una excepción, el alta persiste y la respuesta es 302.
- Test de `SmtpWelcomeNotifier`: el mensaje tiene el asunto, el cuerpo, el remitente y el destinatario esperados.
