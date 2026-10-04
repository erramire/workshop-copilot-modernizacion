# Feature: Consulta de perfiles de estudiantes

## Propósito
Mostrar a cualquier visitante el listado completo de perfiles de estudiantes registrados, con ID, nombre, email y carrera.

## Archivos analizados
- [IndexServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/IndexServlet.java): servlet que mezcla presentación y acceso a datos
- [StudentProfileListServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfileListServlet.java): servlet que genera HTML y JSON y accede a datos
- [controller/StudentController.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/StudentController.java): controller Spring MVC
- [service/StudentService.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java): service
- [util/MyBatisUtil.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/util/MyBatisUtil.java): fábrica de `SqlMapClient`
- [StudentProfile.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfile.java): entidad (POJO)
- [Student_SqlMap.xml](../../legacy/java/jakarta-ee/student-web-app/resources/org/sample/azure/student/msfaa/shared/persistence/xml/Student_SqlMap.xml): mapeo SQL (`listStudent`)
- [index.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/index.jsp) y [spring-index.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/spring-index.jsp): vistas

## Reglas de negocio (extraídas del código)
1. Devuelve **todos** los registros, sin filtro, sin orden y sin paginación. Evidencia: `Student_SqlMap.xml:9`.
2. En el flujo Spring MVC, si la consulta falla se muestra una lista vacía ("No student profiles found.") en vez de un error. Evidencia: `StudentService.java:28-31`.
3. En el flujo legacy `/`, si la consulta falla se muestra el mensaje técnico de la excepción. Evidencia: `IndexServlet.java:40`.
4. En `/studentProfileList`, si la consulta falla se imprime el mensaje y se responde con HTTP 500. Evidencia: `StudentProfileListServlet.java:57-58`.
5. Los datos se escapan antes de pintarse en HTML para evitar XSS. Evidencia: `index.jsp:6`, `spring-index.jsp:6`, `StudentProfileListServlet.java:73`.

## Workflows
### Listado vía Spring MVC (`GET /app/` o `GET /app/students`)
1. Entrada: ninguna.
2. Validación: no aplica.
3. `StudentController.index` o `listStudents` (`StudentController.java:21`, `:39`) llaman a `StudentService.getAllStudents` (`StudentService.java:19`).
4. Consulta con `MyBatisUtil.getSqlMapClient().openSession()` y `queryForList("...listStudent")` (`StudentService.java:25-26`).
5. Salida: `spring-index.jsp` con el atributo `students`.

### Listado legacy (`GET /`)
1. `IndexServlet.doGet` (`IndexServlet.java:22`) abre la sesión iBATIS directamente, sin pasar por el service (L30-L31).
2. Hace forward a `index.jsp` con `students` o con `error` (L53).

### Listado con JSON (`GET /studentProfileList`)
1. `StudentProfileListServlet.doGet` (`StudentProfileListServlet.java:27`) consulta directamente (L39-L42).
2. Escribe una tabla HTML y, debajo, el resultado de `ObjectMapper.writeValueAsString(students)` escapado (L53). **No es una API**: la respuesta es `text/html`.

## Modelo de datos
- Entidad `StudentProfile` (`StudentProfile.java:9`)
  - Campos: `id` (int), `name`, `email` y `major` (String)
  - Relaciones: ninguna
  - Restricciones: solo la PK en BD (`create_table.sql`). No hay borrado lógico ni auditoría

## Endpoints / superficie expuesta
- `GET /` → `IndexServlet.doGet` (`IndexServlet.java:22`). También recibe cualquier URL sin mapeo
- `GET /app/` → `StudentController.index` (`StudentController.java:21`)
- `GET /app/students` → `StudentController.listStudents` (`StudentController.java:39`)
- `GET /studentProfileList` → `StudentProfileListServlet.doGet` (`StudentProfileListServlet.java:27`)
- `/index.jsp` y `/spring-index.jsp` se pueden pedir directamente (salen sin datos)

## Dependencias
- Internas: ninguna otra feature.
- Externas: MySQL (`student_profiles`) vía JNDI `jdbc/StudentDB`; Jackson 1.x (solo en `/studentProfileList`).

## Autorización y seguridad
- No hay autenticación ni autorización: **cualquiera puede ver el email de todos los estudiantes** (exposición de datos personales).
- Se muestran mensajes de excepción al usuario en `/` y `/studentProfileList` (filtración de información).
- Datos sensibles: email (PII).

## APIs no portables detectadas
- `javax.servlet.*` en 2 servlets → `jakarta.servlet.*` (ver [inventory/javax-usages.md](../inventory/javax-usages.md)).
- iBATIS 2 (`com.ibatis.sqlmap.client.SqlMapSession`) en 3 clases.
- Jackson 1.x (`org.codehaus.jackson.map.ObjectMapper`) en `StudentProfileListServlet.java:6`.
- log4j 1.x (`org.apache.log4j.Logger`) en 4 clases.
- JSP con scriptlets, que Spring Boot no soporta en JAR ejecutable.

## Deuda técnica observada
- La misma consulta tiene 4 endpoints, 3 implementaciones de acceso a datos y 3 formas distintas de tratar los errores.
- `StudentController.index` y `listStudents` son idénticos.
- Un servlet genera HTML a mano con `PrintWriter`.
- Cast sin comprobar a `(List<StudentProfile>)`.
- La documentación del sample (`doc/architecture.md`) habla de "REST endpoints con JSON", pero no existe ninguno.

## Riesgo de migración
**Medio**. La lógica es trivial, pero hay que decidir qué URLs se mantienen (por compatibilidad con enlaces existentes) y unificar cómo se tratan los errores.
