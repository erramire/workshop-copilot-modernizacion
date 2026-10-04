# Feature: Notificación de bienvenida por email

## Propósito
Enviar un correo de bienvenida al estudiante cuando se registra su perfil. Hoy solo ocurre en el flujo legacy.

## Archivos analizados
- [AddStudentServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java): servlet (método `sendEmail`)
- [WebContent/WEB-INF/web.xml](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml): `resource-ref mail/StudentMailSession`
- [liberty_config/server-docker.xml](../../legacy/java/jakarta-ee/student-web-app/liberty_config/server-docker.xml): `mailSession` (SMTP)

## Reglas de negocio (extraídas del código)
1. El correo se envía después del commit de la inserción y solo en `POST /addStudent`. Evidencia: `AddStudentServlet.java:55-60`.
2. Destinatario: el email del formulario. Asunto: `Welcome, <name>!`. Cuerpo: texto fijo en inglés con el nombre. Evidencia: `AddStudentServlet.java:101-103`.
3. Remitente: `noreply@example.com`, definido en la configuración del servidor. Evidencia: `server-docker.xml:24`.
4. Si el envío falla, solo queda en el log; el usuario ve "Student added successfully!". Evidencia: `AddStudentServlet.java:62-64`, `:82-87`.

## Workflows
### Envío del correo de bienvenida
1. Entrada: `to` (email) y `name`.
2. Validación: ninguna.
3. Lookup JNDI de `java:comp/env/mail/StudentMailSession` (`AddStudentServlet.java:98-99`).
4. Construye un `MimeMessage` y obtiene los destinatarios con `InternetAddress.parse(to, false)` (`AddStudentServlet.java:100-101`).
5. Llama a `Transport.send(msg)` de forma síncrona, dentro de la propia petición HTTP (`AddStudentServlet.java:104`).

## Modelo de datos
- No persiste nada: no hay registro de correos enviados ni reintentos.

## Endpoints / superficie expuesta
- Indirecta, a través de `POST /addStudent`.

## Dependencias
- Internas: feature 02 (registro), solo en el flujo legacy.
- Externas: servidor SMTP. La configuración actual es `localhost:25`, usuario `user`, password `changeit` y `mailSessionID="SendGridMailSession"` (`server-docker.xml:22-27`); ese ID sugiere que originalmente se usaba SendGrid. En docker-compose no hay servidor SMTP, así que **en el entorno local el envío falla siempre** y solo queda en el log.

## Autorización y seguridad
- `InternetAddress.parse(to, false)` acepta **listas de direcciones separadas por comas**. Con un único POST anónimo se puede mandar el correo a varios destinatarios arbitrarios, lo que permite **usar la app como relay de spam**.
- No hay rate limiting ni CAPTCHA.
- Las credenciales SMTP están en texto plano en `server-docker.xml:27`.
- El nombre del usuario se inserta sin sanear en el asunto y en el cuerpo.

## APIs no portables detectadas
- `javax.mail.*` (5 imports) → `jakarta.mail.*`.
- Lookup JNDI manual con `new InitialContext()`. Spring Boot embebido no expone `java:comp/env`, así que hay que sustituirlo por `JavaMailSender` (`spring.mail.*`) o por un servicio gestionado (Azure Communication Services Email o SendGrid). La elección se hace en Fase 2.

## Deuda técnica observada
- El envío es síncrono y bloquea la respuesta HTTP; no hay reintentos ni cola.
- El texto del correo está escrito directamente en el código.
- La funcionalidad solo existe en uno de los dos flujos de alta.

## Riesgo de migración
**Medio**. El código es corto, pero depende de una integración externa que hoy no funciona en local, y hay que elegir el proveedor de correo en Azure.
