# Bitácora de migración: student-web-app

> Ejecuta `@spring-legacy-migration` · Plan: [docs/migration-plan.md](../docs/migration-plan.md) · Estrategia híbrida ([ADR-002](../docs/adr/ADR-002-estrategia-migracion.md)) · Origen `legacy/java/jakarta-ee/student-web-app` (solo lectura) · Destino `src/student-web-app/`

## [2026-10-04] Paso 0: fundación del proyecto

- Creado `src/student-web-app/` con:
  - `pom.xml`: parent `spring-boot-starter-parent` 3.5.16, Java 21 y las dependencias finales de ADR-004.
  - `StudentWebApplication`.
  - `.gitignore` con `target/`, necesario porque el `.gitignore` de la raíz no lo ignora.
  - `.gitattributes`, que fuerza fin de línea LF en `mvnw`.
- Maven Wrapper generado con `maven-wrapper-plugin` 3.3.4 para Maven 3.9.16.
- Verificación:
  - `./mvnw -v` muestra Maven 3.9.16 y Java 21.0.12 (Temurin).
  - `./mvnw -B verify` termina OK.

## [2026-10-04] Paso 1: línea base y cambio de namespace con OpenRewrite (ADR-003)

**Preparación**
- Copiados tal cual los 9 `.java` de `legacy/.../src/org` a `src/main/java`, conservando los paquetes `org.sample.azure.student.coreft.*`.
- Añadidas las dependencias temporales: `javax.servlet-api` 4.0.1, `javax.mail-api` 1.6.2 y `log4j` 1.2.17, las tres con scope `provided`, más `ibatis-sqlmap` 2.3.0.
- Cambio manual: en `StudentProfileListServlet`, el import `org.codehaus.jackson.map.ObjectMapper` pasa a `com.fasterxml.jackson.databind.ObjectMapper`.
- Línea base `javax`: `./mvnw compile` OK.

**OpenRewrite**
- Plugin `rewrite-maven-plugin` 6.46.1, con `rewrite-migrate-java` 3.42.1 y `rewrite-logging-frameworks` 3.32.0. `BUILD SUCCESS` en 48 s.
- `JavaxMigrationToJakarta` modificó 4 archivos y 24 imports (`javax.servlet` ×19, `javax.mail` ×5): `AddStudentServlet`, `IndexServlet`, `StudentProfileListServlet` y `CommonHttpServletFilter`. `javax.naming` no se toca porque es del JDK.
- `Log4j1ToSlf4j1` modificó 6 archivos: los 3 servlets, `StudentService`, `StudentController` y `AddStudentController`. `org.apache.log4j.Logger` pasa a `org.slf4j.Logger` y `LoggerFactory`, y las concatenaciones se convierten a logging parametrizado (`{}`).

**Cambios que la receta hizo en el `pom.xml` y que se revirtieron**
- Las dependencias `javax` se cambiaron a `jakarta.servlet-api` y `jakarta.mail-api` con scope compile. Se eliminan porque ya las aportan Tomcat embebido y `spring-boot-starter-mail`.
- `log4j` 1.2.17 se cambió a `log4j-api` y `log4j-core` 2.26.1. Se eliminan porque el logging va por SLF4J y Logback, y `log4j-core` entraría en conflicto con `spring-boot-starter-logging`.

**Verificación**
- `./mvnw compile` OK.
- La búsqueda de imports `javax.servlet`, `javax.mail`, `org.apache.log4j` y `org.codehaus.jackson` no devuelve nada.
- `legacy/` sigue sin cambios, comprobado con `git status` en el repositorio del taller y en el clon `legacy/java`.
- No hizo falta el plan B manual.

**Pendiente**
- `ibatis-sqlmap` se queda hasta el paso 4.
- Los servlets y el filtro copiados no están registrados (no hay `web.xml`); solo existen para que OpenRewrite tuviera algo que migrar.

## [2026-10-04] Paso 2: plataforma base (ADR-005, ADR-008, ADR-009, ADR-010)

**Archivos creados**
- `application.yml`, con las propiedades de ADR-010:
  - H2 en modo MySQL, `ddl-auto=validate` y `open-in-view=false`.
  - Actuator con base path `/` y probes activados.
  - Email desactivado y cookies `HttpOnly` con `SameSite=Lax`.
  - Exclusión de `UserDetailsServiceAutoConfiguration`.
- `application-prod.yml`: cookie `Secure`.
- `db/migration/V1__create_student_profiles.sql`: el esquema legacy, sin `DROP`.
- `config/SecurityConfig`: `permitAll`, con CSRF y cabeceras por defecto.
- `presentation/GlobalExceptionHandler`: respeta el código de estado de las excepciones `ErrorResponse` (404, 405…), solo registra los 5xx y siempre devuelve la vista genérica `error`.
- `templates/error.html`, `templates/fragments/layout.html` (fragmentos `head` y `nav`) y `static/css/site.css`, que unifica los estilos inline de las JSP.

**Tests**
- `PlatformAcceptanceTest`:
  - Flyway crea la tabla `student_profiles`.
  - AC-18: `/health`, `/health/liveness` y `/health/readiness` responden UP sin detalles.
  - AC-17: una URL desconocida devuelve 404 con la página genérica.

**Verificación**
- `./mvnw -B verify`: 5 tests OK.
- Arranque real con `java -jar`:
  - Arranca en 9,8 s; Flyway aplica V1 y no se genera ninguna contraseña por defecto.
  - `/health` y `/health/readiness` responden 200 con `UP`, y `/does-not-exist` responde 404.
  - Las respuestas llevan las cabeceras `X-Content-Type-Options`, `X-Frame-Options` y `Cache-Control`.

## [2026-10-04] Paso 3: feature 01, consulta (ADR-006, ADR-007, ADR-008)

**Correspondencia legacy → target**

| Legacy | Target |
| --- | --- |
| `coreft.StudentProfile` (POJO `Serializable`) | `domain.StudentProfile`: `@Entity`, `@Table("student_profiles")`, `Integer id` con `IDENTITY` y `@Column(length = 255)` |
| `MyBatisUtil` + `listStudent` (SqlMap) | `infrastructure.persistence.StudentProfileRepository` (`JpaRepository`) |
| `StudentService.getAllStudents()` (se tragaba los errores) | `application.StudentService.listStudents()`: `findAll(Sort.by("id"))` con `@Transactional(readOnly = true)` y los errores propagados |
| `StudentController.index` y `listStudents`, duplicados; `IndexServlet` | `presentation.StudentController`: `GET /app`, `/app/` y `/app/students` |
| `StudentProfileListServlet` (HTML con JSON) | Redirección 302 a `/app/students` desde `LegacyRedirectController` |
| `spring-index.jsp` e `index.jsp` | `templates/students/list.html` |

**Reglas preservadas**
- Mismas columnas (ID, Name, Email, Major).
- Mismos textos: "No student profiles found." y "Unable to load student data.".
- Escape HTML de los valores.

**Cambios intencionales**, según MIGRATION-SCOPE:
- Orden explícito por id.
- Un error de base de datos muestra un aviso genérico en lugar de la lista vacía o del detalle técnico, y no aparece la fila "No student profiles found.".

**Transición**
- `StudentService.saveStudent` mantiene temporalmente el alta con iBATIS para el `AddStudentController` copiado. Se sustituye en el paso 4.
- En el `AddStudentController` copiado se corrigieron los imports por el cambio de paquete: se quitó el import sin uso de `StudentProfile`.

**Eliminados**
- `coreft/controller/StudentController`, `coreft/IndexServlet`, `coreft/StudentProfileListServlet`, `coreft/StudentProfile` y `coreft/service/StudentService`.

**Tests**
- `StudentProfileRepositoryTest` (`@DataJpaTest` sobre H2 en modo MySQL con Flyway): id generado, AC-01 (orden) y AC-08 (duplicados).
- `StudentServiceTest`: AC-01.
- `StudentControllerTest` (`@WebMvcTest`): AC-01 en las 3 URLs, AC-02, AC-03 y AC-04.
- `LegacyRedirectControllerTest`: AC-15 para `/` y `/studentProfileList`.

**Verificación:** `./mvnw -B verify`, 17 tests OK.

## [2026-10-04] Paso 4: feature 02, registro (ADR-006, ADR-007, ADR-008, ADR-009)

**Correspondencia legacy → target**

| Legacy | Target |
| --- | --- |
| `AddStudentController` (`@RequestParam` sin validación) y `AddStudentServlet` | `StudentController` con `GET` y `POST /app/add-student`, `@Valid StudentForm` y patrón PRG con mensaje flash |
| `StudentService.saveStudent()` (iBATIS, transacción manual, devolvía `boolean`) | `StudentService.register()`: `@Transactional` y `repository.save()`; los errores se propagan |
| `spring-add-student.jsp` y `add_student_profile.jsp` | `templates/students/form.html` |
| `POST /addStudent` y `GET /addStudent` | `GET /addStudent` redirige con 302 a `/app/add-student`; `POST` queda retirado (403 sin token CSRF) |

**Reglas preservadas**
- Mismas etiquetas, placeholders y campos `required` (`type="email"`).
- Mismo mensaje de éxito: "Student {name} has been added successfully!".
- Mismo mensaje de error: "Failed to save student. Please try again.".
- Se siguen admitiendo emails duplicados.

**Reglas nuevas** (MIGRATION-SCOPE):
- Validación en el servidor: `@NotBlank`, `@Email` y `@Size(max = 255)`.
- Recorte de espacios con `StringTrimmerEditor`, solo en el binder de `studentForm`.
- CSRF obligatorio.
- El log solo registra el id: `Student registered with id={}`.

**Eliminados**
- Paquete `coreft` completo: `AddStudentController`, `AddStudentServlet`, `MyBatisUtil` y `CommonHttpServletFilter`.
- Dependencia `ibatis-sqlmap`: `dependency:tree` ya no muestra ninguna coincidencia con `ibatis`.

**Tests**
- `StudentControllerTest`: AC-05, AC-06, AC-07 (5 casos inválidos, valores conservados y recorte de espacios), AC-09 y AC-10.
- `LegacyRedirectControllerTest`: AC-15 completo y AC-16.
- `StudentServiceTest`: `register`.
- `StudentRegistrationAcceptanceTest` (`@SpringBootTest` con H2 real): AC-06 de punta a punta, AC-08 vía HTTP y AC-11, comprobando el log capturado con `OutputCaptureExtension`.

**Verificación:** `./mvnw -B verify`, 34 tests OK.

## [2026-10-04] Paso 5: feature 03, notificación de bienvenida (ADR-011)

**Correspondencia legacy → target**

| Legacy | Target |
| --- | --- |
| `AddStudentServlet.sendEmail()`: JNDI `mail/StudentMailSession` e `InternetAddress.parse(to, false)`; solo en `POST /addStudent` | Interfaz `application.WelcomeNotifier` con dos implementaciones: `infrastructure.mail.SmtpWelcomeNotifier` (`JavaMailSender`, activa con `app.mail.welcome.enabled=true`) y `NoOpWelcomeNotifier` (por defecto) |
| El envío ocurría dentro de la petición, después de `commitTransaction()` | `StudentService.register()` publica `StudentRegisteredEvent`, y `WelcomeNotificationListener` lo atiende con `@TransactionalEventListener` (fase `AFTER_COMMIT`) |

**Reglas preservadas**
- Asunto `Welcome, {name}!`, mismo cuerpo y remitente configurable (`noreply@example.com` por defecto).
- Un fallo de envío no deshace el alta y el usuario sigue viendo el mensaje de éxito.

**Reglas nuevas**
- El correo se envía en todas las altas cuando está activado (FUN-01).
- Un único destinatario: `@Email` en el formulario y `MimeMessageHelper` rechaza listas.
- El fallo se registra como WARN con el id y el tipo de excepción, nunca con la dirección.
- `StudentRegisteredEvent.toString()` no incluye datos personales.

**Comportamiento a tener en cuenta**
- Si `app.mail.welcome.enabled=true` pero no se define `SPRING_MAIL_HOST`, la aplicación no arranca porque falta el bean `JavaMailSender`. Es un fallo rápido y explícito ante una configuración incompleta.

**Tests**
- `SmtpWelcomeNotifierTest`: AC-13 (contenido del mensaje) y AC-14 (el error no se propaga y el log no lleva el email).
- `WelcomeEmailAcceptanceTest`, con el envío activado y `JavaMailSender` simulado: AC-13 de punta a punta después del commit, y AC-14 (el alta se mantiene y el usuario ve el éxito).
- `StudentRegistrationAcceptanceTest`: AC-12 (por defecto se usa `NoOpWelcomeNotifier` y no existe ningún `JavaMailSender`).
- `StudentServiceTest`: se publica el evento.

**Verificación:** `./mvnw -B verify`, 40 tests OK.

## [2026-10-04] Paso 6: contenedor y observabilidad (ADR-004, ADR-010)

**Archivos**
- `Dockerfile` multi-stage:
  - Build: `eclipse-temurin:21-jdk-alpine` con `./mvnw package -DskipTests`.
  - Runtime: `eclipse-temurin:21-jre-alpine`, usuario `app` con uid y gid 1001, `EXPOSE 8080`.
  - El `ENTRYPOINT` añade `-javaagent` solo si existe `APPLICATIONINSIGHTS_CONNECTION_STRING`.
- `.dockerignore`.
- `pom.xml`: `maven-dependency-plugin` copia `applicationinsights-agent` 3.7.9 a `target/agent/` en la fase `package`.

**Verificación (AC-19)**
- `docker build -t petclinic-modern:local .`: imagen de 504 MB. `docker run --entrypoint id` muestra `uid=1001(app)`.
- `docker run -p 8080:8080`, sin variables de entorno:
  - `/health` responde 200 con `UP`.
  - `GET /`, `/addStudent` y `/studentProfileList` responden 302 hacia `/app/`, `/app/add-student` y `/app/students`.
  - `/desconocida` responde 404.
  - `POST` sin token CSRF responde 403. Con token, responde 302 a `/app/`, el listado muestra "Student Alice Johnson has been added successfully!" y el registro, y en el log solo aparece `Student registered with id=1`.
- Con una `APPLICATIONINSIGHTS_CONNECTION_STRING` ficticia, el log muestra "Application Insights Java Agent 3.7.9 started successfully". El arranque pasa de 9,8 s a 18,8 s, lo que confirma R-10.

**Incidencias durante la verificación (no son del producto)**
- El primer intento de `curl` falló porque bash interpretó el `!` del patrón de `grep` como expansión de historial. Se resolvió con `set +H` y comillas simples.
- `curl --retry-connrefused` no reintenta mientras `docker-proxy` acepta la conexión pero la app todavía no está lista. Se resolvió con `--retry-all-errors`.

**Nota:** en Maven Central ya existe `applicationinsights-agent` 3.7.10 (versión de parche). Se mantiene la 3.7.9 que fija ADR-004; Dependabot propondrá la actualización.

## [2026-10-04] Paso 7: cierre y controles de calidad

**Comprobaciones del plan**
- AC-21: el `grep` de `javax.servlet`, `javax.mail`, `org.apache.log4j`, `org.codehaus.jackson` y `com.ibatis` en `src` no devuelve nada, y `dependency:tree` tampoco.
- AC-20:
  - En `src/main/resources` solo aparecen `password: ""` (valor vacío) y `PRIMARY KEY` (SQL). En `src/main/java`, nada.
  - La imagen solo tiene las variables de la imagen base (`PATH`, `JAVA_HOME`, `LANG`…).
- Comprobación manual en el JAR de cambios intencionales que no tienen test:
  - `/app/students/` responde 404.
  - Columnas ID, Name, Email y Major.
  - Navegación "Students" y "Add Student", y botón "Add Student".
  - Ninguna aparición de "Technology Stack", "Spring MVC" ni enlaces legacy.

**Archivos**
- `pom.xml`: perfil `security-scan` con `dependency-check-maven` 13.0.0 (ADR-004).
  - `failBuildOnCVSS=7`.
  - La clave se lee de la variable de entorno `NVD_API_KEY` mediante `nvdApiKeyEnvironmentVariable`; nunca se escribe en el POM.
  - El effective POM confirma que el plugin solo entra en el build con `-Psecurity-scan`.
  - **No se ejecutó** porque no hay clave (BF-03).
- `.github/dependabot.yml`: ecosistema `maven` en `/src/student-web-app`, semanal.
- `migration/parity-notes.md`: cómo se verificó cada AC, y los cambios intencionales con su evidencia.
- `migration/blockers-found.md`: BF-01 a BF-05.

**Tests reforzados** (en `StudentControllerTest`, sin tests nuevos)
- AC-03: además, se comprueba que el error queda en el log como ERROR (`OutputCaptureExtension`).
- AC-05: además, se comprueban los placeholders del legacy y el atributo `required`.

**Verificación**
- `./mvnw -B verify`: 40 tests OK.
- `git status -- legacy/` y `git -C legacy/java status` no muestran cambios.
