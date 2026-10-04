# Feature: Registro de perfil de estudiante

## Propósito
Permitir que un visitante dé de alta un perfil de estudiante (nombre, email y carrera) desde un formulario web.

## Archivos analizados
- [AddStudentServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java): servlet con formulario, alta y email
- [controller/AddStudentController.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/AddStudentController.java): controller Spring MVC
- [service/StudentService.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java): service (`saveStudent`)
- [util/MyBatisUtil.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/util/MyBatisUtil.java): fábrica de `SqlMapClient`
- [Student_SqlMap.xml](../../legacy/java/jakarta-ee/student-web-app/resources/org/sample/azure/student/msfaa/shared/persistence/xml/Student_SqlMap.xml): mapeo SQL (`addStudent`)
- [add_student_profile.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/add_student_profile.jsp), [spring-add-student.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/spring-add-student.jsp) y [spring-index.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/spring-index.jsp): vistas
- [database/create_table.sql](../../legacy/java/jakarta-ee/student-web-app/database/create_table.sql): esquema

## Reglas de negocio (extraídas del código)
1. `name`, `email` y `major` son obligatorios **solo en el navegador** (`required`, `type="email"`). Evidencia: `spring-add-student.jsp:56-66`, `add_student_profile.jsp:54-64`.
2. El servidor no valida nada: acepta valores vacíos, emails inválidos y textos de hasta 255 caracteres, que es el límite de la columna. Evidencia: `AddStudentController.java:29-31`, `AddStudentServlet.java:37-39`.
3. No se controlan duplicados: el mismo email se puede registrar varias veces porque no hay UNIQUE. Evidencia: `create_table.sql`.
4. El alta es una sola inserción dentro de una transacción. Evidencia: `StudentService.java:52-62`, `AddStudentServlet.java:47-55`.
5. **Solo el flujo legacy (`POST /addStudent`) envía el email de bienvenida**, después del commit. Evidencia: `AddStudentServlet.java:60`. El flujo Spring MVC no lo envía.
6. En el flujo legacy, si el email falla no se deshace el alta ni se avisa: el usuario ve el alta como correcta. Evidencia: `AddStudentServlet.java:56-64`, `:82`.
7. En el flujo Spring MVC, tras el alta se redirige al listado con un mensaje flash que incluye el nombre. Evidencia: `AddStudentController.java:42-43`, `:56`.

## Workflows
### Alta vía Spring MVC (`POST /app/add-student`)
1. Entrada: `name`, `email` y `major` como `@RequestParam` (`AddStudentController.java:29-31`).
2. Validación: ninguna en el servidor.
3. Llama a `StudentService.saveStudent` (`AddStudentController.java:38`, que lleva a `StudentService.java:45`).
4. Inserta con `session.insert("...addStudent", Map)` en una transacción manual (`StudentService.java:51-62`).
5. Salida: `redirect:/app/` con el mensaje flash `successMessage` o `errorMessage` (`AddStudentController.java:42-56`).

### Alta legacy (`POST /addStudent`)
1. Entrada: `request.getParameter(...)` (`AddStudentServlet.java:37-39`).
2. Validación: ninguna.
3. Inserta directamente con iBATIS, sin pasar por el service (`AddStudentServlet.java:46-55`).
4. Efecto secundario: `sendEmail(email, name)` (`AddStudentServlet.java:60`). Ver [03-notificacion-bienvenida.md](03-notificacion-bienvenida.md).
5. Salida si va bien: HTML inline "Student added successfully!" (`AddStudentServlet.java:84-87`), sin patrón PRG, así que recargar la página vuelve a enviar el formulario.
6. Salida si falla: forward a `add_student_profile.jsp` con el atributo `errorMsg` (L90). La JSP lee `errorMessage`, así que **el error no se muestra**.

## Modelo de datos
- `StudentProfile` / tabla `student_profiles` (ver [inventory/persistence.md](../inventory/persistence.md)).
  - `id` autogenerado; el resto de columnas admite NULL.
  - Sin UNIQUE en `email`.
  - El ID generado no se recupera después del insert.

## Endpoints / superficie expuesta
- `GET /app/add-student` → `AddStudentController.showAddStudentForm` (`AddStudentController.java:22`)
- `POST /app/add-student` → `AddStudentController.addStudent` (`AddStudentController.java:27`)
- `GET /addStudent` → `AddStudentServlet.doGet` (`AddStudentServlet.java:27`)
- `POST /addStudent` → `AddStudentServlet.doPost` (`AddStudentServlet.java:34`)

## Dependencias
- Internas: feature 03 (notificación de bienvenida), solo en el flujo legacy; feature 01 (el flujo Spring MVC redirige al listado).
- Externas: MySQL vía JNDI `jdbc/StudentDB`; servidor SMTP vía JNDI `mail/StudentMailSession` (solo flujo legacy).

## Autorización y seguridad
- El endpoint de escritura es **público**: sin autenticación, **sin protección CSRF** y sin límite de peticiones.
- No hay validación ni normalización de datos en el servidor.
- Se escriben datos personales (nombre, email) en los logs a nivel INFO sin sanear (*log injection*, CWE-117): `AddStudentController.java:34`, `StudentService.java:46`, `AddStudentServlet.java:45`.
- Las JSP del formulario imprimen variables sin escapar (`spring-add-student.jsp:43,49`; `add_student_profile.jsp:41,47`). Es un XSS latente: hoy no le llegan datos del usuario, pero bastaría con pasárselos en el futuro.
- Se devuelven mensajes de excepción al usuario (`AddStudentController.java:52-53`).

## APIs no portables detectadas
- `javax.servlet.*` en `AddStudentServlet` (ver [inventory/javax-usages.md](../inventory/javax-usages.md)).
- iBATIS 2 en `StudentService` y `AddStudentServlet`.
- log4j 1.x en 3 clases.
- JSP con scriptlets, que Spring Boot no soporta en JAR ejecutable.

## Deuda técnica observada
- Hay dos implementaciones del mismo caso de uso y se comportan distinto en el email, la respuesta y el manejo de errores.
- La transacción manual está duplicada.
- El service devuelve un `boolean` en lugar de lanzar excepciones de dominio.
- Hay dos JSP de formulario casi idénticas. Además, `add_student_profile.jsp:31` tiene un enlace roto a `/students`, y su texto dice "uses Spring MVC" cuando el formulario hace POST al servlet legacy `/addStudent`.

## Riesgo de migración
**Medio**. La lógica es sencilla, pero antes de unificar los dos flujos hay que acordar con el cliente el comportamiento correcto: si se envía email en todas las altas, qué validaciones aplican y qué hacer con los duplicados.
