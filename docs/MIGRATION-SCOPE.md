# Alcance de la migración: student-web-app

> **Fase 2 (planning)** · 2026-10-04
>
> Este documento es el contrato que tiene que cumplir `@spring-legacy-migration`. Los tests hacen referencia a los identificadores de los criterios de aceptación (AC-xx), según [ADR-012](adr/ADR-012-pruebas.md).

## Dentro del alcance
- **Sistema:** de `legacy/java/jakarta-ee/student-web-app` (solo lectura) a `src/student-web-app/`.
- **Features:**
  - [01 Consulta de perfiles de estudiantes](features/01-consulta-estudiantes.md)
  - [02 Registro de perfil de estudiante](features/02-registro-estudiante.md)
  - [03 Notificación de bienvenida por email](features/03-notificacion-bienvenida.md)
- **Aspectos transversales:** seguridad básica, configuración, health, observabilidad e imagen de contenedor.

## Fuera del alcance
- El resto de proyectos de `legacy/java/`.
- Los cambios en `infra/` y en los labs. Los hace la Fase 4 o el facilitador (ver el handoff en [migration-plan.md](migration-plan.md)).
- Login con Entra ID, Key Vault, Azure Database for MySQL, el aprovisionamiento de Azure Communication Services, rate limiting y cualquier API REST.
- La migración de datos desde el MySQL legacy.
- Funcionalidad nueva: editar o borrar estudiantes, paginación o búsqueda.

## Contrato de URLs

| Método | URL | Respuesta | AC |
| --- | --- | --- | --- |
| GET | `/app`, `/app/` y `/app/students` | 200, listado | AC-01 a AC-04 |
| GET | `/app/add-student` | 200, formulario | AC-05 |
| POST | `/app/add-student` | Datos válidos: 302 a `/app/`. Datos inválidos: 200 con el formulario y sus errores. Sin token CSRF: 403 | AC-06 a AC-11 |
| GET | `/` | 302 a `/app/` | AC-15 |
| GET | `/addStudent` | 302 a `/app/add-student` | AC-15 |
| GET | `/studentProfileList` | 302 a `/app/students` | AC-15 |
| POST | `/addStudent` | 403: URL retirada, sin token CSRF ni handler | AC-16 |
| GET | `/health`, `/health/liveness` y `/health/readiness` | 200, con `status` = `UP` y sin detalles | AC-18 |
| Cualquiera | Cualquier otra URL | 404, página de error genérica | AC-17 |

## Criterios de aceptación

### Feature 01: consulta
- **AC-01:** `GET /app`, `/app/` y `/app/students` muestran una tabla con las columnas ID, Name, Email y Major, con todos los registros ordenados por id de menor a mayor.
- **AC-02:** si no hay registros, la tabla muestra una única fila con el texto "No student profiles found.".
- **AC-03:** si la base de datos falla, la página se muestra igualmente: con el aviso "Unable to load student data.", sin detalle técnico y con la tabla vacía. El error queda en el log como ERROR.
- **AC-04:** todos los valores se escapan. Por ejemplo, un nombre `<script>alert(1)</script>` aparece como texto.

### Feature 02: registro
- **AC-05:** `GET /app/add-student` muestra el formulario con los campos `name`, `email` (de tipo `email`) y `major`. Los tres son `required`, se mantienen las etiquetas y placeholders del legacy, y el formulario lleva token CSRF.
- **AC-06:** un `POST` válido guarda el registro, redirige a `/app/` y muestra "Student {name} has been added successfully!".
- **AC-07:** validación en el servidor:
  - `name` y `major` son obligatorios y no pueden estar en blanco.
  - `email` es obligatorio y tiene que ser una única dirección válida.
  - Ningún campo puede pasar de 255 caracteres.
  - Los espacios al principio y al final se recortan.
  - Si algo no es válido, se responde con 200, el formulario muestra los errores de cada campo y conserva lo que se había escrito. No se guarda nada.
- **AC-08:** se permiten emails duplicados, igual que en el legacy.
- **AC-09:** un `POST` sin token CSRF recibe 403 y no se guarda nada.
- **AC-10:** si la base de datos falla al guardar, se redirige a `/app/` con el mensaje "Failed to save student. Please try again.", sin detalle técnico.
- **AC-11:** los logs del alta no contienen ni el nombre ni el email. Solo el id.

### Feature 03: notificación
- **AC-12:** con `app.mail.welcome.enabled=false`, que es el valor por defecto, no se intenta enviar ningún correo.
- **AC-13:** con el envío activado, tras cada alta confirmada se envía un correo a la dirección registrada, desde `app.mail.welcome.from`. El asunto es `Welcome, {name}!` y el cuerpo es el mismo que en el legacy.
- **AC-14:** si el envío falla, el alta no se deshace y la respuesta no cambia: el usuario ve el mensaje de éxito. El fallo queda en el log como WARN, sin el email.

### Compatibilidad y plataforma
- **AC-15:** las URLs legacy de tipo GET redirigen (302) según el contrato de URLs.
- **AC-16:** `POST /addStudent` queda retirada.
- **AC-17:** las URLs desconocidas devuelven 404 con una página genérica, sin stack trace.
- **AC-18:** `/health`, `/health/liveness` y `/health/readiness` devuelven 200 con `status` = `UP` y sin detalles.
- **AC-19:** la imagen Docker arranca sin ninguna variable de entorno, escucha en el puerto 8080, se ejecuta con un usuario no root (uid 1001) y responde en `/health`.
- **AC-20:** no hay secretos en el código, en `application*.yml` ni en la imagen.
- **AC-21:** en `src/student-web-app` no queda ningún import de `javax.servlet`, `javax.mail`, `org.apache.log4j`, `org.codehaus.jackson` ni `com.ibatis`, ni ninguna de esas librerías en el árbol de dependencias.

## Cambios intencionales respecto al legacy

| Legacy | Target | Motivo | Referencia |
| --- | --- | --- | --- |
| 4 URLs de listado con 3 comportamientos distintos | 3 URLs bajo `/app` y redirecciones desde las legacy | Unificación | [ADR-007](adr/ADR-007-capa-web.md) |
| Email solo en `POST /addStudent` | Email en todas las altas, si está activado | FUN-01 | [ADR-011](adr/ADR-011-notificacion-email.md) |
| Sin validación en el servidor | Bean Validation | SEC-05 | [ADR-009](adr/ADR-009-seguridad.md) |
| Errores técnicos visibles para el usuario | Mensajes genéricos | SEC-06 | [ADR-009](adr/ADR-009-seguridad.md) |
| En el flujo Spring, un error de BD aparecía como lista vacía | Aviso genérico (AC-03) | FUN-03 | [ADR-007](adr/ADR-007-capa-web.md) |
| El error del alta legacy no se mostraba | Errores visibles en el formulario | FUN-03 | [ADR-007](adr/ADR-007-capa-web.md) |
| `/studentProfileList` incrustaba JSON escapado en el HTML | Se elimina | No era una API | [ADR-007](adr/ADR-007-capa-web.md) |
| Se aceptaba un POST sin token, incluido el `curl` de la documentación | 403 sin token CSRF | SEC-04 | [ADR-009](adr/ADR-009-seguridad.md) |
| Cualquier URL desconocida mostraba el listado | 404 | Los servlets estaban mapeados en `/` | [ADR-007](adr/ADR-007-capa-web.md) |
| Se aceptaban URLs con barra final, como `/app/students/` | 404, salvo `/app/` | Cambio de Spring 6 | [ADR-007](adr/ADR-007-capa-web.md) |
| Orden de los registros indefinido | Ordenados por id | Resultado determinista | [ADR-006](adr/ADR-006-persistencia.md) |
| Datos personales en los logs | Logs sin datos personales | SEC-07 | [ADR-009](adr/ADR-009-seguridad.md) |
| Bloques "Technology Stack", navegación hacia URLs legacy y botón "Add Student (Spring MVC)" | Se eliminan; navegación a "Students" y "Add Student"; botón "Add Student" | Textos obsoletos | [ADR-008](adr/ADR-008-frontend.md) |
| Datos persistentes en MySQL | H2 en memoria por defecto | Taller sin infraestructura de base de datos | [ADR-005](adr/ADR-005-base-de-datos.md) |

## Lo que tiene que quedar igual
- Las etiquetas y placeholders del formulario.
- Las columnas de la tabla y su orden.
- Los textos de éxito y de error del flujo Spring: `Student {name} has been added successfully!` y `Failed to save student. Please try again.`
- El esquema de `student_profiles`: mismas columnas y tipos, se siguen admitiendo duplicados y NULL.
- El asunto y el cuerpo del correo de bienvenida.
- El estilo visual: colores, tipografía y disposición.

## Definition of Done de la migración
- `./mvnw -B verify` termina en verde y todos los criterios de aceptación tienen al menos un test.
- `docker build` funciona, y `docker run -p 8080:8080` responde en `/health` y permite completar los flujos de consulta y alta.
- Las comprobaciones de AC-20 y AC-21 están superadas.
- `migration/migration-log.md` tiene una entrada por paso, y `migration/parity-notes.md` explica cómo se verificó cada criterio de aceptación.
- `git status` no muestra ningún cambio en `legacy/`.
