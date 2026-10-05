# ADR-011: Notificación de bienvenida por email

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisión 11 del planning, aceptada en el chat)

## Contexto
- El legacy solo envía el email de bienvenida en el flujo `POST /addStudent`, no en el de Spring MVC (FUN-01).
- Lo hace con JavaMail, buscando la sesión SMTP por JNDI. Si el envío falla, el usuario igualmente ve el alta como correcta.
- `InternetAddress.parse(to, false)` acepta listas de direcciones, lo que permite usar la app como relay de spam (SEC-03).
- La configuración de Liberty apunta a `localhost:25` con credenciales en claro. En docker-compose no hay servidor SMTP, así que el envío falla siempre.
- El Bicep no crea ningún servicio de correo.

## Opciones consideradas
1. **`JavaMailSender` (`spring-boot-starter-mail`), desactivado por defecto y activable por configuración.** Cuando está activo se envía en todas las altas.
2. **Eliminar la funcionalidad.**
   - En contra: se pierde comportamiento de negocio.
3. **SDK de Azure Communication Services Email.**
   - En contra: acopla el código a Azure y requiere aprovisionar el servicio durante el taller.

## Decisión
Opción 1.

### Diseño
- Puerto `application.WelcomeNotifier` con dos implementaciones:
  - `infrastructure.mail.SmtpWelcomeNotifier`: usa `JavaMailSender` y está activa cuando `app.mail.welcome.enabled=true`.
  - `infrastructure.mail.NoOpWelcomeNotifier`: la que se usa por defecto. Solo registra a nivel DEBUG que el envío está desactivado.
- Se envía en **todas** las altas que se confirmen, es decir, después del commit de la transacción. Con esto se unifica el comportamiento de los dos flujos legacy.
- Si el envío falla, el error se captura y se registra como WARN, sin la dirección de email. Ni el alta ni la respuesta al usuario cambian, igual que en el legacy.

### Mensaje
Igual que en el legacy:
- Asunto: `Welcome, {name}!`.
- Cuerpo: `Dear {name},\n\nYour student profile has been created successfully.\n\nRegards,\nAdmin`.
- Remitente: `app.mail.welcome.from`.

### Garantías
- Un único destinatario: la dirección ya está validada con `@Email`, y `MimeMessageHelper` rechaza cadenas con varias direcciones.
- El envío es síncrono, con timeouts SMTP de 5 segundos (`spring.mail.properties.mail.smtp.connectiontimeout`, `timeout` y `writetimeout`). Un envío asíncrono o con cola queda fuera de alcance.

### Configuración
- `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` (587 por defecto) y `SPRING_MAIL_USERNAME`.
- `SPRING_MAIL_PASSWORD`, que se pasa como secreto.
- STARTTLS activado (`spring.mail.properties.mail.smtp.starttls.enable=true`).

### En Azure (fuera de alcance)
Para activar el envío, la opción prevista es el relay SMTP de Azure Communication Services Email, con las credenciales guardadas como secretos de la Container App. En el taller no se aprovisiona.

## Consecuencias
- **Positivas:**
  - Las dos altas se comportan igual.
  - La app deja de poder usarse como relay.
  - No depende de ningún proveedor y en el taller no hace falta infraestructura.
- **Negativas:**
  - En el taller el email no se envía (está desactivado por defecto).
  - El envío síncrono añade latencia al alta cuando está activo.
- **Riesgos a monitorear:** R-07 en [risks.md](../risks.md).

## Referencias
- [features/03-notificacion-bienvenida.md](../features/03-notificacion-bienvenida.md)
- [blockers.md](../blockers.md) (FUN-01, SEC-03)
