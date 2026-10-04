# Feature: Notificación de bienvenida por correo

## Propósito
Enviar un correo de bienvenida al estudiante recién registrado.

## Archivos analizados
- [AddStudentServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java): el único componente que envía correo (`sendEmail`)
- [web.xml](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml): `resource-ref` `mail/StudentMailSession`
- [server-docker.xml](../../legacy/java/jakarta-ee/student-web-app/liberty_config/server-docker.xml): `mailSession` SMTP de Liberty

## Reglas de negocio (extraídas del código)
1. El correo se envía **solo** cuando el alta entra por `POST /addStudent` (servlet), y siempre después del commit. Evidencia: `AddStudentServlet.java:55-60`. El alta por `/app/add-student` no envía correo.
2. El destinatario es el email del formulario, sin validar en el servidor (`:101`).
3. El asunto es `Welcome, <name>!` y el cuerpo es un texto fijo con el nombre (`:102-103`).
4. El remitente es `noreply@example.com`, configurado en el servidor (`server-docker.xml:24`).
5. Si el envío falla, **el alta no se revierte**. El error solo queda en el log, con el texto engañoso "Error adding student", y el usuario ve el mensaje de éxito (`success` ya vale `true` en `:56`).

## Workflows
### Envío
1. `sendEmail(email, name)` (`AddStudentServlet.java:95`).
2. Lookup JNDI de `java:comp/env/mail/StudentMailSession` (`:98-99`).
3. Arma un `MimeMessage` y lo envía con `Transport.send` (`:100-104`). El envío es **síncrono**, dentro del request HTTP.

## Configuración actual (Liberty)
- `server-docker.xml:22-27`: `host="localhost"`, `port="25"`, `transportProtocol="smtp"`, `user="user"`, `password="changeit"` (en texto plano) y `mailSessionID="SendGridMailSession"`.
- `docker-compose.yml` no levanta ningún servidor SMTP. **En el entorno Docker del proyecto, el envío siempre falla** y el usuario no se entera.

## Modelo de datos
- No persiste nada. Toma `name` y `email` del alta.

## Endpoints / superficie expuesta
- No tiene endpoint propio. Se dispara desde `POST /addStudent`.

## Dependencias
- Internas: feature [02-alta-estudiante](02-alta-estudiante.md), solo la ruta servlet.
- Externas: servidor SMTP (JNDI `mail/StudentMailSession`) y JavaMail 1.6 (`javax.mail`) del servidor.

## Autorización y seguridad
- Un endpoint público, sin autenticación ni CSRF, envía correo a cualquier dirección: es un vector de abuso (spam y reputación del dominio remitente).
- Credenciales SMTP en texto plano en `server-docker.xml:27`.
- SMTP por el puerto 25, sin TLS configurado.
- El `name` entra en el asunto y el cuerpo sin sanitizar.
- Se escribe en el log la dirección de correo del destinatario (`:58`, `:96`, `:105`).

## APIs no portables detectadas
- `javax.mail.*` (5 imports) pasa a `jakarta.mail.*`.
- `javax.naming.InitialContext` con JNDI del contenedor: Spring Boot con contenedor embebido no ofrece JNDI. Hay que configurar el correo de otra forma (decisión de Fase 2).

## Deuda técnica observada
- El envío es síncrono dentro del request, sin reintentos ni cola.
- La ruta Spring MVC no envía correo: hay divergencia funcional entre las dos rutas de alta.
- Los mensajes de log son engañosos.

## Riesgo de migración
**Medio.** El código es trivial, pero hay que decidir si la funcionalidad existe en el target (hoy solo está en una ruta y no funciona en el entorno Docker) y qué servicio de correo usar en Azure.

## Escenarios de paridad (para tests de caracterización)
1. Alta por la ruta con correo: se intenta enviar 1 mensaje a la dirección indicada, con el asunto `Welcome, <name>!`.
2. SMTP no disponible: el registro queda guardado y el usuario ve éxito.
