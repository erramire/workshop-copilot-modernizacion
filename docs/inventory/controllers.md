# Inventario: controllers, servlets y endpoints

> **Sistema analizado:** `legacy/java/jakarta-ee/student-web-app` · **Fase 1 (assessment)** · 2026-10-04
> Context root en Liberty: `/` ([server-docker.xml L43](../../legacy/java/jakarta-ee/student-web-app/liberty_config/server-docker.xml#L43)).

## Resumen

| Tipo | Cantidad | Handlers |
| --- | --- | --- |
| Controllers Spring MVC (`@Controller`) | 2 | 4 |
| `@RestController` / `@ResponseBody` | 0 | 0 |
| Servlets clásicos (`HttpServlet`) | 3 | 4 |
| Filtros (`Filter`) | 1, sin registrar | — |
| Struts actions | 0 (no hay `struts.xml` ni `struts-config.xml`) | — |
| JSP accesibles directamente | 4 | — |

## Controllers Spring MVC

| Controller | URL base | Endpoints | Servicios usados | Archivo |
| --- | --- | --- | --- | --- |
| StudentController | `/app` (servlet mapping) | 2 (GET) | StudentService | [controller/StudentController.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/StudentController.java) |
| AddStudentController | `/app` (servlet mapping) | 2 (GET, POST) | StudentService | [controller/AddStudentController.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/AddStudentController.java) |

### Endpoints

| Método | URL efectiva | Handler | Parámetros | Retorno | Destino |
| --- | --- | --- | --- | --- | --- |
| GET | `/app/` | `StudentController.index` ([L21](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/StudentController.java#L21)) | `Model` | Nombre de vista | `spring-index.jsp` |
| GET | `/app/students` | `StudentController.listStudents` ([L39](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/StudentController.java#L39)) | `Model` | Nombre de vista | `spring-index.jsp` |
| GET | `/app/add-student` | `AddStudentController.showAddStudentForm` ([L22](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/AddStudentController.java#L22)) | — | Nombre de vista | `spring-add-student.jsp` |
| POST | `/app/add-student` | `AddStudentController.addStudent` ([L27](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/AddStudentController.java#L27)) | `@RequestParam` `name`, `email`, `major` y `RedirectAttributes` | Redirect | `redirect:/app/` con flash `successMessage` o `errorMessage` |

Observaciones:
- `index` y `listStudents` son idénticos: mismo código, misma vista y mismos datos.
- Inyección por campo con `@Autowired` ([StudentController L18](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/StudentController.java#L18), [AddStudentController L19](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/AddStudentController.java#L19)).
- No hay validación (`@Valid`, Bean Validation) ni `@ExceptionHandler`/`@ControllerAdvice`. Cada handler atrapa `Exception` y muestra `e.getMessage()` al usuario.
- `RedirectAttributes` usa `SessionFlashMapManager`, que guarda el mensaje en la sesión HTTP. Con varias réplicas sin afinidad de sesión, el mensaje flash se puede perder.
- `AddStudentController` importa `StudentProfile` y `Model` sin usarlos.

## Servlets clásicos (fuera de Spring)

| Servlet | URL (`web.xml`) | Métodos | Acceso a datos | Salida | Archivo |
| --- | --- | --- | --- | --- | --- |
| IndexServlet | `/` (default servlet) | GET | `MyBatisUtil` directo | Forward a `/index.jsp` | [IndexServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/IndexServlet.java) |
| AddStudentServlet | `/addStudent` | GET, POST | `MyBatisUtil` directo + JavaMail por JNDI | GET: forward a `/add_student_profile.jsp`. POST: HTML inline, o forward al formulario si falla | [AddStudentServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java) |
| StudentProfileListServlet | `/studentProfileList` | GET | `MyBatisUtil` directo + Jackson 1.x | HTML escrito con `PrintWriter` + JSON escapado | [StudentProfileListServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfileListServlet.java) |

### Endpoints

| Método | URL | Handler | Parámetros | Comportamiento |
| --- | --- | --- | --- | --- |
| GET | `/` (y cualquier URL sin mapeo) | `IndexServlet.doGet` ([L22](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/IndexServlet.java#L22)) | — | Lista estudiantes en `index.jsp`. Si falla, pone el mensaje de la excepción en `error` ([L40](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/IndexServlet.java#L40)) |
| GET | `/addStudent` | `AddStudentServlet.doGet` ([L27](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L27)) | — | Muestra `add_student_profile.jsp` |
| POST | `/addStudent` | `AddStudentServlet.doPost` ([L34](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L34)) | `name`, `email`, `major` (form) | Inserta, hace commit, **envía el email de bienvenida** y escribe HTML inline, sin patrón PRG |
| GET | `/studentProfileList` | `StudentProfileListServlet.doGet` ([L27](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfileListServlet.java#L27)) | — | Tabla HTML con el JSON escapado debajo. Si falla, imprime el mensaje y lanza `RuntimeException` ([L58](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfileListServlet.java#L58)), lo que da HTTP 500 |

Observaciones:
- Los servlets **no usan `StudentService`**: repiten la lógica de acceso a datos (ver [services-repositories.md](services-repositories.md)).
- `AddStudentServlet` guarda el error en el atributo `errorMsg` ([L90](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L90)), pero la JSP lee `errorMessage` ([add_student_profile.jsp L37](../../legacy/java/jakarta-ee/student-web-app/WebContent/add_student_profile.jsp#L37)). **El usuario nunca ve el error.**
- El enlace "Add Another Student" lleva a `/`, que es el listado y no el formulario ([L87](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L87)).
- Hay un mensaje de log obsoleto, "Redirecting to HelloServlet" ([L83](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L83)).

## Filtros

| Filtro | Registrado | Función | Archivo |
| --- | --- | --- | --- |
| CommonHttpServletFilter | **No**: no hay `<filter>` en `web.xml` ni `@WebFilter` | Guarda la "IP real" de `X-Forwarded-For`/`X-Client-IP` en el atributo `RealClientIP` | [filter/CommonHttpServletFilter.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/filter/CommonHttpServletFilter.java) |

- Es código muerto: ningún componente lee `RealClientIP`.
- Si se activara, confiaría en cabeceras que controla el cliente (spoofing de IP). Ver [blockers.md](../blockers.md).

## Vistas JSP

| JSP | La usa | Escapa la salida | Observación |
| --- | --- | --- | --- |
| [index.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/index.jsp) | IndexServlet | Sí, con `esc()` | Scriptlets y enlaces absolutos |
| [spring-index.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/spring-index.jsp) | StudentController | Sí, con `esc()` | Muestra los mensajes flash `successMessage`/`errorMessage` |
| [spring-add-student.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/spring-add-student.jsp) | AddStudentController | **No** ([L43](../../legacy/java/jakarta-ee/student-web-app/WebContent/spring-add-student.jsp#L43), [L49](../../legacy/java/jakarta-ee/student-web-app/WebContent/spring-add-student.jsp#L49)) | El formulario hace POST a `/app/add-student` |
| [add_student_profile.jsp](../../legacy/java/jakarta-ee/student-web-app/WebContent/add_student_profile.jsp) | AddStudentServlet | **No** ([L41](../../legacy/java/jakarta-ee/student-web-app/WebContent/add_student_profile.jsp#L41), [L47](../../legacy/java/jakarta-ee/student-web-app/WebContent/add_student_profile.jsp#L47)) | El formulario hace POST a `/addStudent`. Enlace roto a `/students` ([L31](../../legacy/java/jakarta-ee/student-web-app/WebContent/add_student_profile.jsp#L31)) |

- Todas usan scriptlets (`<% %>`), sin JSTL ni taglibs. El escape HTML está copiado tres veces: `esc()` en dos JSP y `escapeHtml()` en un servlet.
- Están en la raíz pública de `WebContent/`, no en `WEB-INF/`, así que se pueden pedir directamente (p. ej. `/spring-index.jsp`) sin pasar por el controller.
- Los enlaces son absolutos (`/app/`, `/studentProfileList`, `/addStudent`) y dan por hecho que el context root es `/`.

## Mapa de URLs y solapamientos

| Capacidad | URLs que la implementan | Diferencias de comportamiento |
| --- | --- | --- |
| Listar estudiantes | `/`, `/app/`, `/app/students`, `/studentProfileList` | Cada una maneja los errores a su manera: mensaje técnico, lista vacía o HTTP 500 |
| Alta de estudiante | `POST /addStudent`, `POST /app/add-student` | Solo `/addStudent` envía email. Una responde con HTML inline y la otra con redirect + flash |
| Formulario de alta | `GET /addStudent`, `GET /app/add-student` | Dos JSP casi idénticas |

- Dentro de Spring MVC no hay `@RequestMapping` ambiguos.
- Spring 5.3 acepta barra final (`/app/students/`). Spring 6 desactiva por defecto el *trailing slash match*, así que es un cambio de comportamiento a validar con el cliente.
