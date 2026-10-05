# Plan de migración: student-web-app

> **Fase 2 (planning)** · 2026-10-04 · Lo ejecuta `@spring-legacy-migration` · Estrategia: híbrida ([ADR-002](adr/ADR-002-estrategia-migracion.md))

## Reglas de ejecución
- **Origen:** `legacy/java/jakarta-ee/student-web-app`, de solo lectura.
- **Destino:** `src/student-web-app/`.
- Todos los comandos se lanzan desde `src/student-web-app/`, con `JAVA_HOME` apuntando a Temurin 21 (`$JAVA_HOME_21` en el Codespace).
- **Cada paso termina:**
  - Con su verificación en verde: `./mvnw -B verify`, salvo el paso 1, que solo compila.
  - Con una entrada en `migration/migration-log.md` que recoja los cambios, las decisiones que se aplicaron, los tests añadidos y cualquier bloqueo.
- **Si aparece un bloqueo que el plan no contemplaba**, se apunta en `migration/blockers-found.md` y se escala. No se toman decisiones de diseño nuevas.
- **Tests:** los de cada feature se escriben antes de portarla ([ADR-012](adr/ADR-012-pruebas.md)).

## Estrategia por módulo

| Elemento legacy | Estrategia | Paso | Destino |
| --- | --- | --- | --- |
| `StudentProfile.java` | Se copia y después se anota con JPA | 1, 3 | `domain/StudentProfile` |
| `service/StudentService.java` | Se copia y después se refactoriza a JPA | 1, 3, 4 | `application/StudentService` |
| `controller/StudentController.java` | Se copia y después se reescribe | 1, 3 | `presentation/StudentController` |
| `controller/AddStudentController.java` | Se copia y después se fusiona con `StudentController` | 1, 4 | `presentation/StudentController` |
| `IndexServlet` y `StudentProfileListServlet` | Se copian solo para OpenRewrite y se eliminan | 1, 3 | `presentation/LegacyRedirectController` |
| `AddStudentServlet` | Se copia y se elimina; el envío de email se reescribe | 1, 4, 5 | `LegacyRedirectController` e `infrastructure/mail` |
| `filter/CommonHttpServletFilter` | Se copia y se elimina | 1, 4 | `server.forward-headers-strategy` |
| `util/MyBatisUtil` | Se copia y se elimina | 1, 4 | `infrastructure/persistence/StudentProfileRepository` |
| `sql-map-config.xml` y `Student_SqlMap.xml` | No se copian | — | Repositorio JPA |
| `web.xml`, `applicationContext*.xml` y `spring-servlet.xml` | No se copian | — | Autoconfiguración, `application.yml` y `config/` |
| `log4j.properties` | No se copia | — | Logback por defecto |
| Las 4 JSP | Se reescriben | 3, 4 | `templates/students/*.html` |
| `database/create_table.sql` | Se reescribe sin `DROP` | 2 | `db/migration/V1__create_student_profiles.sql` |
| `build.xml`, `build.properties`, `setup-docker.*` y `WEB-INF/lib` | No se copian | — | `pom.xml` y Maven Wrapper |
| `Dockerfile`, `docker-compose.yml` y `liberty_config/` | Solo se reescribe el Dockerfile; el resto no se copia | 6 | `Dockerfile` multi-stage |

## Paso 0: fundación del proyecto
ADRs: [ADR-002](adr/ADR-002-estrategia-migracion.md) y [ADR-004](adr/ADR-004-build-empaquetado.md).

- Crear `src/student-web-app/pom.xml` con el parent `spring-boot-starter-parent` 3.5.16, Java 21 y las dependencias finales de ADR-004. No crear el proyecto con start.spring.io, porque puede que ya no ofrezca la versión 3.5.x.
- Generar el Maven Wrapper (Maven 3.9.x) con `maven-wrapper-plugin`.
- Crear `org.sample.azure.student.StudentWebApplication` y un `.gitignore` que excluya `target/`.
- Crear `migration/migration-log.md` en la raíz del repositorio.

**Verificación:**
```bash
./mvnw -v          # debe mostrar Java 21
./mvnw -B verify
```

## Paso 1: línea base y cambio de namespace con OpenRewrite
ADR: [ADR-003](adr/ADR-003-namespace-jakarta.md).

1. Copiar los 9 `.java` de `legacy/java/jakarta-ee/student-web-app/src/` a `src/main/java/`, conservando los paquetes.
2. Añadir las dependencias temporales de ADR-004: `javax.servlet-api`, `javax.mail-api` y `log4j` con scope `provided`, e `ibatis-sqlmap`.
3. Cambiar a mano el import de Jackson 1 en `StudentProfileListServlet` por `com.fasterxml.jackson.databind.ObjectMapper`.
4. Verificar que la línea base con `javax` compila:
   ```bash
   ./mvnw -B compile
   ```
5. Ejecutar OpenRewrite:
   ```bash
   ./mvnw -B org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
     -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-migrate-java:3.42.1,org.openrewrite.recipe:rewrite-logging-frameworks:3.32.0 \
     -Drewrite.activeRecipes=org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta,org.openrewrite.java.logging.slf4j.Log4j1ToSlf4j1
   ```
6. Quitar del `pom.xml` las dependencias temporales de `javax` y `log4j`, y las APIs `jakarta` que haya añadido la receta. `ibatis-sqlmap` se queda hasta el paso 4.
7. Verificar:
   ```bash
   ./mvnw -B compile
   grep -rnE "import (javax\.(servlet|mail)|org\.apache\.log4j|org\.codehaus\.jackson)" src/main/java   # no debe devolver nada
   ```
8. Anotar en la bitácora los archivos y los imports que cambió OpenRewrite. Si fue necesario el plan B (migración manual), anotarlo también.

## Paso 2: plataforma base
ADRs: [ADR-005](adr/ADR-005-base-de-datos.md), [ADR-008](adr/ADR-008-frontend.md), [ADR-009](adr/ADR-009-seguridad.md) y [ADR-010](adr/ADR-010-configuracion-observabilidad.md).

- Crear `application.yml` y `application-prod.yml` con las propiedades de ADR-010.
- Crear `db/migration/V1__create_student_profiles.sql` según ADR-005.
- Crear `config/SecurityConfig` según ADR-009, excluyendo `UserDetailsServiceAutoConfiguration`.
- Crear `presentation/GlobalExceptionHandler`, `templates/error.html`, `templates/fragments/layout.html` y `static/css/site.css`.
- Tests:
  - El contexto arranca con Flyway aplicado.
  - AC-17, probando con una URL que no esté mapeada (los controllers copiados todavía mapean `/`, `/students` y `/add-student`).
  - AC-18.

**Verificación:**
```bash
./mvnw -B verify
./mvnw spring-boot:run      # y en otra terminal:
curl -s localhost:8080/health
```

## Paso 3: feature 01, consulta
ADRs: [ADR-006](adr/ADR-006-persistencia.md), [ADR-007](adr/ADR-007-capa-web.md) y [ADR-008](adr/ADR-008-frontend.md).

- Escribir primero los tests de AC-01 a AC-04 y la parte de AC-15 que afecta a `/` y a `/studentProfileList`.
- Mover `StudentProfile` a `domain` y añadirle las anotaciones JPA. Crear `infrastructure/persistence/StudentProfileRepository`.
- Mover `StudentService` a `application` y crear `listStudents()` con JPA, ordenado por id y con `@Transactional(readOnly = true)`. El método de alta sigue usando iBATIS hasta el paso 4.
- Crear `presentation/StudentController` con `GET /app`, `/app/` y `/app/students`, y la plantilla `templates/students/list.html`.
- Crear `presentation/LegacyRedirectController` con `GET /` y `GET /studentProfileList`.
- Eliminar `coreft/controller/StudentController`, `coreft/IndexServlet` y `coreft/StudentProfileListServlet`.

**Verificación:** `./mvnw -B verify`.

## Paso 4: feature 02, registro
ADRs: [ADR-006](adr/ADR-006-persistencia.md), [ADR-007](adr/ADR-007-capa-web.md), [ADR-008](adr/ADR-008-frontend.md) y [ADR-009](adr/ADR-009-seguridad.md).

- Escribir primero los tests de AC-05 a AC-11, AC-15 completo y AC-16.
- Crear `presentation/StudentForm` con Bean Validation y el recorte de espacios.
- Añadir a `StudentController` el `GET` y el `POST` de `/app/add-student` (patrón PRG con mensaje flash) y la plantilla `templates/students/form.html`.
- Crear `StudentService.register(...)` con JPA y `@Transactional`. Los logs solo llevan el id, sin datos personales.
- Añadir a `LegacyRedirectController` el `GET /addStudent`.
- Eliminar todo el paquete `coreft`: `AddStudentController`, `AddStudentServlet`, `MyBatisUtil` y `CommonHttpServletFilter`. Quitar también la dependencia `ibatis-sqlmap`.

**Verificación:**
```bash
./mvnw -B verify
./mvnw -B dependency:tree | grep -i ibatis   # no debe devolver nada
```

## Paso 5: feature 03, notificación de bienvenida
ADR: [ADR-011](adr/ADR-011-notificacion-email.md).

- Escribir primero los tests de AC-12 a AC-14.
- Crear la interfaz `application/WelcomeNotifier` y sus implementaciones `infrastructure/mail/SmtpWelcomeNotifier` (activa con `app.mail.welcome.enabled=true`) y `NoOpWelcomeNotifier` (la que se usa por defecto).
- Invocar el envío después del commit del alta. Los errores se capturan y se registran sin el email.

**Verificación:** `./mvnw -B verify`.

## Paso 6: contenedor y observabilidad
ADRs: [ADR-004](adr/ADR-004-build-empaquetado.md) y [ADR-010](adr/ADR-010-configuracion-observabilidad.md).

- Crear el `Dockerfile` multi-stage y el `.dockerignore`.
- Copiar el agente de Application Insights con `maven-dependency-plugin` y adjuntarlo de forma condicional en el `ENTRYPOINT`.

**Verificación (AC-19):**
```bash
docker build -t petclinic-modern:local .
docker run --rm --entrypoint id petclinic-modern:local          # uid=1001
docker run --rm -p 8080:8080 petclinic-modern:local             # y en otra terminal:
curl -s localhost:8080/health
```
Además, en el navegador: el listado, un alta válida con su mensaje de éxito y la redirección desde `/`.

## Paso 7: cierre y controles de calidad
- **AC-21:**
  ```bash
  grep -rnE "javax\.(servlet|mail)|org\.apache\.log4j|org\.codehaus\.jackson|com\.ibatis" src   # no debe devolver nada
  ./mvnw -B dependency:tree | grep -E "log4j:log4j:jar|org\.codehaus\.jackson|ibatis|javax\.(servlet|mail)"   # no debe devolver nada
  ```
- **AC-20:** revisar a mano el resultado de `grep -rniE "password|secret|token" src/main/resources`. Solo pueden aparecer claves con valores vacíos.
- **Escaneo de CVEs (opcional, necesita `NVD_API_KEY`):** `./mvnw -B -Psecurity-scan verify`.
- Crear `.github/dependabot.yml` según ADR-004.
- Escribir `migration/parity-notes.md`: para cada criterio de aceptación, cómo se verificó, y la tabla de cambios intencionales de [MIGRATION-SCOPE.md](MIGRATION-SCOPE.md).
- Comprobar que `git status -- legacy/` no muestra ningún cambio.

## Handoff a Fase 4 y cambios en el material del taller
Son recomendaciones para el facilitador y para `@azure-architect`. **No** se aplican en la migración.

| Dónde | Cambio | Motivo |
| --- | --- | --- |
| `labs/lab-03-iac/README.md`, paso 8 (Linux y Windows) | `docker build -t $ACR_SERVER/petclinic:workshop src/student-web-app/` en lugar de `legacy/java/`, y añadir la etiqueta inmutable | [ADR-002](adr/ADR-002-estrategia-migracion.md), [ADR-013](adr/ADR-013-cutover.md) |
| `infra/main.bicep` (`petclinicApp`) | Probes `Liveness` en `/health/liveness` y `Readiness` en `/health/readiness`, puerto 8080, y `maxReplicas: 1` | [ADR-005](adr/ADR-005-base-de-datos.md), [ADR-010](adr/ADR-010-configuracion-observabilidad.md), R-05 |
| `labs/lab-02-java/README.md`, paso 4 | Respuestas del planning: no hay Hibernate ni Struts; la decisión real es iBATIS 2 a JPA | Que el lab coincida con el sistema real |
| `labs/lab-02-java/README.md`, pasos 6 a 8 | Rutas en `src/student-web-app`. La app es "Student Management System", no PetClinic. Los commits van al repositorio del taller, no al clon `legacy/java` | Ídem |
| `labs/lab-02-java/README.md`, entregables | Usar la numeración de ADRs de este plan, que es la que lee `@spring-legacy-migration` | Coherencia con los agentes |
