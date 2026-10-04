# Feature: Alta de perfil de estudiante

## Propósito
Registrar un nuevo estudiante (nombre, email y carrera) desde un formulario web.

## Archivos analizados
- [AddStudentController.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/AddStudentController.java): controller Spring MVC de `/app/add-student`
- [AddStudentServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java): servlet de `/addStudent`
- [StudentService.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java): service
- [MyBatisUtil.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/util/MyBatisUtil.java): acceso a datos
- [Student_SqlMap.xml](../../legacy/java/jakarta-ee/student-web-app/resources/org/sample/azure/student/msfaa/shared/persistence/xml/Student_SqlMap.xml): statement `addStudent`
- [spring-add-student.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/spring-add-student.jsp) y [add_student_profile.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/add_student_profile.jsp): formularios
- [spring-index.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/spring-index.jsp): página de resultado en la ruta Spring

## Reglas de negocio (extraídas del código)
1. `name`, `email` y `major` son obligatorios **solo en el navegador**: atributo HTML `required`, y `type="email"` para el email (`spring-add-student.jsp:61`, `add_student_profile.jsp:59`). **El servidor no valida** formato, longitud ni campos vacíos.
2. Si falta un parámetro:
   - En la ruta Spring, `@RequestParam` obligatorio hace que Spring responda HTTP 400 (`AddStudentController.java:29-31`).
   - En la ruta servlet, el valor ausente se inserta como `NULL` (`AddStudentServlet.java:37-39`).
3. No hay control de duplicados: un mismo email puede registrarse varias veces. La tabla no tiene UNIQUE y el código no lo verifica.
4. La inserción es transaccional (una sola sentencia): `StudentService.java:52-62` y `AddStudentServlet.java:47-55`.
5. El id lo genera la BD (`AUTO_INCREMENT`) y no se devuelve.
6. **Solo la ruta servlet** envía el correo de bienvenida después del commit (`AddStudentServlet.java:60`). Ver [03-notificacion-bienvenida.md](03-notificacion-bienvenida.md).
7. Resultado para el usuario:
   - Ruta Spring: redirect a `/app/` con el flash `successMessage` ("Student <name> has been added successfully!") o `errorMessage` (`AddStudentController.java:42-56`).
   - Ruta servlet: si todo sale bien, HTML inline "Student added successfully!" (`AddStudentServlet.java:85-87`). Si falla, forward al formulario con el atributo `errorMsg` (`:90-91`), **que la JSP no muestra** porque lee `errorMessage`.

## Workflows
### Alta por Spring MVC
1. `GET /app/add-student` muestra `spring-add-student.jsp` (`AddStudentController.java:22`).
2. `POST /app/add-student` (form-urlencoded con `name`, `email`, `major`) llega a `AddStudentController.addStudent` (`:27`).
3. `StudentService.saveStudent` (`StudentService.java:45`) abre la sesión, ejecuta `startTransaction`, el `insert addStudent` y `commitTransaction`.
4. Devuelve `true` o `false`. El controller arma el mensaje flash y redirige a `/app/` (`:56`).
5. `/app/` (feature 01) muestra el mensaje y la lista actualizada.

### Alta por servlet
1. `GET /addStudent` muestra `add_student_profile.jsp` (`AddStudentServlet.java:27-30`).
2. `POST /addStudent` llega a `AddStudentServlet.doPost` (`:34`), que inserta directamente con `MyBatisUtil`, sin pasar por `StudentService` (`:46-55`).
3. Después del commit envía el correo (`:60`).
4. Si todo sale bien, responde con HTML inline y enlaces a `/studentProfileList` y `/`.

## Modelo de datos
- Los parámetros se cargan en un `Map` (`name`, `email`, `major`) y se insertan en `student_profiles` (`Student_SqlMap.xml:13-15`).
- Las restricciones de la tabla son mínimas (ver `docs/inventory/persistence.md`).

## Endpoints / superficie expuesta
- `GET /app/add-student` → `AddStudentController.showAddStudentForm` (`AddStudentController.java:22`)
- `POST /app/add-student` → `AddStudentController.addStudent` (`AddStudentController.java:27`)
- `GET /addStudent` → `AddStudentServlet.doGet` (`AddStudentServlet.java:27`)
- `POST /addStudent` → `AddStudentServlet.doPost` (`AddStudentServlet.java:34`)

## Dependencias
- Internas: feature 01 (página de resultado de la ruta Spring) y feature 03 (correo, solo en la ruta servlet).
- Externas: MySQL vía JNDI, iBATIS 2.3.0 y Log4j 1.2.17.

## Autorización y seguridad
- No hay autenticación: cualquiera puede crear registros.
- Ninguno de los 2 formularios POST tiene protección **CSRF**.
- No hay validación del lado del servidor, así que pueden entrar datos basura o producirse errores de BD (por ejemplo, valores de más de 255 caracteres).
- `spring-add-student.jsp` (líneas 43 y 49) y `add_student_profile.jsp` (líneas 41 y 47) imprimen `successMessage` y `errorMessage` **sin escapar**: es un XSS latente, porque el mensaje flash incluye el nombre ingresado. En el flujo actual ese mensaje se muestra en `spring-index.jsp`, que sí escapa.
- Datos personales (nombre, email) en logs con nivel INFO, y entrada del usuario escrita en el log sin sanitizar (CWE-117): `AddStudentController.java:34`, `StudentService.java:46`, `AddStudentServlet.java:45`.
- El usuario ve `e.getMessage()` (`AddStudentController.java:53`).

## APIs no portables detectadas
- `javax.servlet.*` en `AddStudentServlet`.
- `SqlMapSession` de iBATIS con transacciones manuales.
- JSP con scriptlets.

## Deuda técnica observada
- Hay dos implementaciones del mismo caso de uso que se comportan distinto en el correo, los mensajes y el manejo de errores.
- `AddStudentController` importa `StudentProfile` y no lo usa (`:3`).
- El log de éxito dice "Redirecting to HelloServlet" (`AddStudentServlet.java:83`), un servlet que no existe.
- El enlace "View All Students" apunta a `/students` (`add_student_profile.jsp:31`), que no tiene mapeo y termina en `IndexServlet`.
- El enlace "Add Another Student" de la respuesta de éxito lleva a `/`, el listado, y no al formulario (`AddStudentServlet.java:87`).

## Riesgo de migración
**Medio.** La operación es simple, pero hay que decidir qué comportamiento conservar entre los dos flujos divergentes, y agregar validación y CSRF, que hoy no existen.

## Escenarios de paridad (para tests de caracterización)
1. POST válido: el registro se inserta y aparece un mensaje de éxito con el nombre.
2. POST sin `email`: hoy la ruta Spring responde 400 y la ruta servlet inserta NULL. Hay que definir el comportamiento objetivo.
3. Email duplicado: se inserta (comportamiento actual).
4. BD caída: no se inserta nada y el usuario ve un mensaje de error.
5. Nombre con `<script>`: el mensaje de éxito lo muestra escapado.
