# Feature: Consulta de perfiles de estudiantes

## Propósito
Mostrar a cualquier visitante la lista de estudiantes registrados con su id, nombre, email y carrera.

## Archivos analizados
- [IndexServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/IndexServlet.java): servlet de `/`
- [StudentProfileListServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfileListServlet.java): servlet de `/studentProfileList`
- [StudentController.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/StudentController.java): controller Spring MVC de `/app/` y `/app/students`
- [StudentService.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java): service
- [MyBatisUtil.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/util/MyBatisUtil.java): acceso a datos (iBATIS 2)
- [Student_SqlMap.xml](../../legacy/java/jakarta-ee/student-web-app/resources/org/sample/azure/student/msfaa/shared/persistence/xml/Student_SqlMap.xml): statement `listStudent`
- [StudentProfile.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfile.java): modelo
- [index.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/index.jsp) y [spring-index.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/spring-index.jsp): vistas

## Reglas de negocio (extraídas del código)
1. Se listan **todos** los perfiles, sin filtro, orden ni paginación. Evidencia: `Student_SqlMap.xml:8-10` (SELECT sin `WHERE`, `ORDER BY` ni `LIMIT`).
2. La consulta es pública y no exige autenticación. Evidencia: `web.xml` no tiene `security-constraint`.
3. Los datos se escapan para HTML antes de mostrarse. Evidencia: `index.jsp:6`, `spring-index.jsp:6`, `StudentProfileListServlet.java:73`.
4. Ante un error de BD, cada ruta se comporta distinto:
   - `/`: muestra "Unable to load student data: <detalle técnico>" (`IndexServlet.java:40`).
   - `/app/` y `/app/students`: `StudentService` traga la excepción y devuelve una lista vacía, así que se ve "No student profiles found." (`StudentService.java:28-31`).
   - `/studentProfileList`: escribe el error en la página y responde HTTP 500 (`StudentProfileListServlet.java:55-58`).

## Workflows
### Listado por servlet (`/`)
1. Entrada: `GET /` o cualquier ruta sin mapeo.
2. `IndexServlet.doGet` (`IndexServlet.java:22`) abre una `SqlMapSession` con `MyBatisUtil` (`:30`).
3. Ejecuta `listStudent` (`:31`).
4. Guarda `students` en el request (`:34`) y hace forward a `/index.jsp` (`:53`).

### Listado por Spring MVC (`/app/` y `/app/students`)
1. Entrada: `GET /app/` o `GET /app/students`.
2. Lo atiende `StudentController.index` o `listStudents` (`StudentController.java:21`, `:39`). Los dos métodos son idénticos.
3. Llama a `StudentService.getAllStudents()` (`StudentService.java:19`), que ejecuta `listStudent`.
4. Pone `students` en el modelo y devuelve la vista `spring-index.jsp`.

### Listado con JSON embebido (`/studentProfileList`)
1. Entrada: `GET /studentProfileList`.
2. `StudentProfileListServlet.doGet` (`:27`) ejecuta `listStudent` (`:42`).
3. Arma una tabla HTML con `PrintWriter`, serializa la lista con Jackson 1.x y la imprime escapada dentro del HTML (`:53`). **No es un endpoint JSON**: el Content-Type es `text/html`.

## Modelo de datos
- Entidad `StudentProfile` (`StudentProfile.java:9`)
  - Campos: `id` (int), `name`, `email`, `major` (String)
  - Relaciones: ninguna
- Tabla `student_profiles` (`database/create_table.sql`)
  - `id` es PK auto-incremental. El resto de columnas acepta NULL y no tiene UNIQUE.

## Endpoints / superficie expuesta
- `GET /` → `IndexServlet.doGet` (`IndexServlet.java:22`)
- `GET /studentProfileList` → `StudentProfileListServlet.doGet` (`StudentProfileListServlet.java:27`)
- `GET /app/` → `StudentController.index` (`StudentController.java:21`)
- `GET /app/students` → `StudentController.listStudents` (`StudentController.java:39`)

## Dependencias
- Internas: ninguna.
- Externas: MySQL (DataSource JNDI `jdbc/StudentDB` de Liberty), iBATIS 2.3.0, Jackson 1.9.13 (solo `/studentProfileList`) y Log4j 1.2.17.

## Autorización y seguridad
- No hay autenticación ni autorización.
- Expone datos personales (nombre y email) de todos los estudiantes a cualquier visitante.
- Muestra al usuario mensajes de error con detalle técnico (`ex.getMessage()`, CWE-209).
- Las 3 vistas de listado escapan la salida HTML correctamente.

## APIs no portables detectadas
- `javax.servlet.*` en `IndexServlet` y `StudentProfileListServlet`: pasan a `jakarta.servlet.*` (ver `docs/inventory/javax-usages.md`).
- `org.codehaus.jackson.map.ObjectMapper` (Jackson 1.x, EOL) en `StudentProfileListServlet.java:6`.
- `com.ibatis.sqlmap.client.*` (iBATIS 2, retirado).
- `org.apache.log4j.Logger` (Log4j 1.x, EOL).
- JSP con scriptlets: Spring Boot no las soporta en un JAR ejecutable (ver `docs/blockers.md`, B1).

## Deuda técnica observada
- Hay 4 rutas para la misma consulta, con lógica duplicada en 2 servlets y 1 service.
- `StudentController.index` y `listStudents` son idénticos.
- El manejo de errores es inconsistente entre rutas (ver la regla 4).
- Sin paginación, la respuesta crece de forma lineal con la tabla.
- `IndexServlet` en `/` reemplaza al *default servlet* del contenedor.

## Riesgo de migración
**Bajo.** Es una sola query sin reglas complejas. El esfuerzo está en consolidar rutas y reescribir las vistas JSP.

## Escenarios de paridad (para tests de caracterización)
1. Sin registros: la vista muestra "No student profiles found.".
2. Con N registros: se listan N filas con id, nombre, email y carrera.
3. Un nombre que contiene `<script>` se muestra escapado.
4. Con la BD caída: hay que decidir en Fase 2 cuál de los 3 comportamientos actuales se conserva.
