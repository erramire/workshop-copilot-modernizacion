# Notas de paridad: student-web-app

> Para `@migration-tester` · Contrato: [MIGRATION-SCOPE.md](../docs/MIGRATION-SCOPE.md) · Bitácora: [migration-log.md](migration-log.md) · Estado al cierre (2026-10-04): `./mvnw -B verify` con 40 tests OK

## Cómo ejecutar

```bash
cd src/student-web-app
./mvnw -B verify                                   # tests
./mvnw spring-boot:run                             # app en http://localhost:8080/app/
docker build -t petclinic-modern:local .
docker run --rm -p 8080:8080 petclinic-modern:local
```

La base de datos es H2 en memoria ([ADR-005](../docs/adr/ADR-005-base-de-datos.md)), así que los datos se pierden al reiniciar.

**Alta con `curl`.** El `curl` de la documentación legacy ya no funciona, porque ahora hace falta el token CSRF (R-08):

```bash
TOKEN=$(curl -s -c jar.txt localhost:8080/app/add-student | grep -oP 'name="_csrf" value="\K[^"]+')
curl -s -b jar.txt -c jar.txt -o /dev/null -w '%{http_code} %{redirect_url}\n' -X POST \
  --data-urlencode "_csrf=$TOKEN" --data-urlencode 'name=Alice Johnson' \
  --data-urlencode 'email=alice@example.com' --data-urlencode 'major=Biology' \
  localhost:8080/app/add-student            # 302 http://localhost:8080/app/
```

**Validación en el navegador.** Para probar la validación del servidor (AC-07) desde el navegador hay que saltarse la del cliente (`required` y `type="email"`). Con `curl` y el token es directo.

## Clases de test
Todas están en `src/student-web-app/src/test/java/org/sample/azure/student/`:

- [PlatformAcceptanceTest](../src/student-web-app/src/test/java/org/sample/azure/student/PlatformAcceptanceTest.java): `@SpringBootTest`.
- [StudentRegistrationAcceptanceTest](../src/student-web-app/src/test/java/org/sample/azure/student/StudentRegistrationAcceptanceTest.java): `@SpringBootTest` con H2 real.
- [WelcomeEmailAcceptanceTest](../src/student-web-app/src/test/java/org/sample/azure/student/WelcomeEmailAcceptanceTest.java): `@SpringBootTest` con el email activado y `JavaMailSender` simulado.
- [StudentControllerTest](../src/student-web-app/src/test/java/org/sample/azure/student/presentation/StudentControllerTest.java): `@WebMvcTest` con la configuración de seguridad.
- [LegacyRedirectControllerTest](../src/student-web-app/src/test/java/org/sample/azure/student/presentation/LegacyRedirectControllerTest.java): `@WebMvcTest`.
- [StudentServiceTest](../src/student-web-app/src/test/java/org/sample/azure/student/application/StudentServiceTest.java): Mockito.
- [StudentProfileRepositoryTest](../src/student-web-app/src/test/java/org/sample/azure/student/infrastructure/persistence/StudentProfileRepositoryTest.java): `@DataJpaTest` con H2 en modo MySQL y Flyway.
- [SmtpWelcomeNotifierTest](../src/student-web-app/src/test/java/org/sample/azure/student/infrastructure/mail/SmtpWelcomeNotifierTest.java): Mockito.

## Verificación por criterio de aceptación

| AC | Tests (`Clase#método`) | Verificación manual |
| --- | --- | --- |
| AC-01 | `StudentControllerTest#listShowsAllStudents` (`/app`, `/app/` y `/app/students`), `StudentProfileRepositoryTest#findAllSortedById`, `StudentServiceTest#listStudentsSortsById` | Contenedor: el listado muestra el alta (paso 6). JAR: columnas ID, Name, Email y Major en ese orden (paso 7) |
| AC-02 | `StudentControllerTest#emptyListShowsPlaceholderRow` | — |
| AC-03 | `StudentControllerTest#databaseFailureShowsGenericMessage`: aviso, sin detalle técnico, sin la fila vacía y con log ERROR | — |
| AC-04 | `StudentControllerTest#valuesAreHtmlEscaped` | — |
| AC-05 | `StudentControllerTest#formShowsRequiredFieldsAndCsrfToken`: etiquetas, placeholders y `required` idénticos al legacy, `type="email"` y `_csrf` | Contenedor: el formulario incluye el token (paso 6) |
| AC-06 | `StudentControllerTest#validSubmissionRegistersAndRedirects`, `StudentRegistrationAcceptanceTest#registeredStudentAppearsInList`, `StudentServiceTest#registerSavesStudent` | Contenedor: 302 a `/app/` y "Student Alice Johnson has been added successfully!" (paso 6) |
| AC-07 | `StudentControllerTest#invalidSubmissionShowsFieldErrors` (5 casos: nombre en blanco, carrera vacía, email inválido, varias direcciones y más de 255 caracteres), `#invalidSubmissionKeepsEnteredValues` y `#valuesAreTrimmedBeforeRegistering` | — |
| AC-08 | `StudentProfileRepositoryTest#allowsDuplicateEmails`, `StudentRegistrationAcceptanceTest#duplicateEmailsAreAllowed` | — |
| AC-09 | `StudentControllerTest#submissionWithoutCsrfTokenIsRejected` | Contenedor: `POST` sin token, 403 (paso 6) |
| AC-10 | `StudentControllerTest#databaseFailureOnSaveShowsGenericMessage` | — |
| AC-11 | `StudentRegistrationAcceptanceTest#registrationLogsContainNoPersonalData` | Contenedor: el log solo muestra `Student registered with id=1` (paso 6) |
| AC-12 | `StudentRegistrationAcceptanceTest#welcomeEmailIsDisabledByDefault` | — |
| AC-13 | `WelcomeEmailAcceptanceTest#registrationSendsWelcomeEmailAfterCommit`, `SmtpWelcomeNotifierTest#sendsLegacyWelcomeMessage`, `StudentServiceTest#registerPublishesStudentRegisteredEvent` | **No se probó con un servidor SMTP real** |
| AC-14 | `WelcomeEmailAcceptanceTest#mailFailureDoesNotAffectRegistration`, `SmtpWelcomeNotifierTest#sendFailureIsLoggedWithoutEmail` | — |
| AC-15 | `LegacyRedirectControllerTest#legacyUrlsRedirectToCanonicalUrls` (3 URLs) | Contenedor: 302 en las 3 URLs (paso 6) |
| AC-16 | `LegacyRedirectControllerTest#legacyAddStudentPostIsRetired` | — |
| AC-17 | `PlatformAcceptanceTest#unknownUrlReturnsGenericNotFoundPage` | Contenedor: `/desconocida`, 404 (paso 6) |
| AC-18 | `PlatformAcceptanceTest#healthEndpointsAreUp` (3 endpoints) | Contenedor: `/health`, 200 con `UP` (paso 6) |
| AC-19 | Sin test JUnit (ver la nota). `PlatformAcceptanceTest` demuestra que la app arranca con la configuración por defecto y responde en `/health` | `docker run --entrypoint id` muestra uid 1001. `docker run -p 8080:8080` sin variables: `/health` responde 200 y se completan la consulta y el alta (paso 6) |
| AC-20 | Sin test JUnit (ver la nota) | En `src/main/resources` solo aparecen `password: ""` y `PRIMARY KEY`; en `src/main/java`, nada. La imagen solo tiene las variables de la imagen base y en sus capas no hay valores sensibles. La clave de NVD se lee de una variable de entorno (paso 7) |
| AC-21 | Sin test JUnit (ver la nota) | Ni el `grep` de imports en `src` ni `dependency:tree` devuelven nada (paso 7) |

**Nota sobre AC-19 a AC-21.** El plan ([migration-plan.md](../docs/migration-plan.md), pasos 6 y 7) define estas comprobaciones como comandos, y la Definition of Done de MIGRATION-SCOPE las enumera aparte. Sin embargo, [ADR-012](../docs/adr/ADR-012-pruebas.md) pide al menos un test por criterio. Queda escalado en [blockers-found.md](blockers-found.md) (BF-04).

## Cambios intencionales respecto al legacy

| Legacy | Target | Verificado en |
| --- | --- | --- |
| 4 URLs de listado con 3 comportamientos distintos | 3 URLs bajo `/app` y redirecciones desde las legacy | AC-01 y AC-15 |
| Email solo en `POST /addStudent` | Email en todas las altas, si está activado | AC-13 |
| Sin validación en el servidor | Bean Validation | AC-07 |
| Errores técnicos visibles para el usuario | Mensajes genéricos | AC-03, AC-10 y AC-17 |
| En el flujo Spring, un error de BD aparecía como lista vacía | Aviso genérico, sin la fila "No student profiles found." | AC-03 |
| El error del alta legacy no se mostraba | Errores visibles en el formulario | AC-07 y AC-10 |
| `/studentProfileList` incrustaba JSON escapado en el HTML | Se elimina; la URL redirige a `/app/students` | AC-15 |
| Se aceptaba un POST sin token | 403 sin token CSRF | AC-09 y AC-16 |
| Cualquier URL desconocida mostraba el listado | 404 | AC-17 |
| Se aceptaban URLs con barra final, como `/app/students/` | 404, salvo `/app/` | Manual, paso 7: `/app/students/` responde 404; `/app/` lo cubre AC-01 |
| Orden de los registros indefinido | Ordenados por id | AC-01 |
| Datos personales en los logs | Logs sin datos personales | AC-11 y AC-14 |
| Bloques "Technology Stack", navegación hacia URLs legacy y botón "Add Student (Spring MVC)" | Se eliminan; navegación "Students" y "Add Student"; botón "Add Student" | Manual, paso 7: ninguna aparición de "Technology Stack", "Spring MVC" ni enlaces legacy; la navegación y el botón son los esperados |
| Datos persistentes en MySQL | H2 en memoria por defecto | `PlatformAcceptanceTest#flywayCreatesStudentProfilesTable` |

## Lo que tiene que quedar igual

| Elemento | Evidencia |
| --- | --- |
| Etiquetas y placeholders del formulario | AC-05: texto exacto de `add_student_profile.jsp` y `spring-add-student.jsp` |
| Columnas de la tabla y su orden | AC-01 y comprobación manual del paso 7 |
| Textos de éxito y de error | AC-06 y AC-10, con el texto exacto |
| Esquema de `student_profiles` | `V1__create_student_profiles.sql` es el DDL de `legacy/.../database/create_table.sql` sin el `DROP`. No hay `NOT NULL` ni `UNIQUE`, y `ddl-auto: validate` lo contrasta con la entidad al arrancar |
| Asunto y cuerpo del correo | `SmtpWelcomeNotifierTest#sendsLegacyWelcomeMessage` |
| Estilo visual | `static/css/site.css` unifica los estilos inline de las JSP. **Falta la comparación visual en el navegador**, que queda para `@migration-tester` |

## Límites de la verificación
- **MySQL:** no se ha validado (R-04). Los tests usan H2 en modo MySQL, y Testcontainers con MySQL es opcional según ADR-012.
- **Email real:** no se ha probado. Si se activa con `app.mail.welcome.enabled=true`, hay que definir `SPRING_MAIL_HOST`; si falta, la app no arranca (BF-02).
- **Escaneo OWASP:** no se ha ejecutado porque no hay `NVD_API_KEY` (BF-03).
- **Varias réplicas:** el token CSRF, los mensajes flash y los datos viven en memoria. Con más de una réplica fallarían (R-05 y R-11), por eso se recomienda `maxReplicas: 1` en el handoff.
