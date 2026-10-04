# Inventario: controllers, servlets, filtros y vistas

> Sistema: `legacy/java/jakarta-ee/student-web-app/`. Las rutas son relativas a esa carpeta.
> El context root en Liberty es `/` (`liberty_config/server-docker.xml`, línea 43).

## Resumen

| Tipo | Cantidad |
| --- | --- |
| Controllers Spring MVC | 2 |
| Endpoints Spring MVC | 4 (3 GET, 1 POST) |
| Servlets `HttpServlet` registrados en `web.xml` | 3 |
| Endpoints de servlets | 4 (3 GET, 1 POST) |
| Filtros | 1 (no registrado: código muerto) |
| Struts actions | 0 (no hay `struts.xml`, `struts-config.xml` ni jars de Struts) |
| Endpoints REST (`@RestController` / `@ResponseBody`) | 0 |
| Vistas JSP | 4 |

## Controllers Spring MVC (bajo `/app/*`)

| Controller | URL base | Endpoints | Servicios usados | Archivo |
| --- | --- | --- | --- | --- |
| StudentController | `/app` | 2 (GET) | StudentService | `src/org/sample/azure/student/coreft/controller/StudentController.java` |
| AddStudentController | `/app` | 2 (GET, POST) | StudentService | `src/org/sample/azure/student/coreft/controller/AddStudentController.java` |

### Endpoints

| Método | URL efectiva | Handler | Parámetros | Retorno | Evidencia |
| --- | --- | --- | --- | --- | --- |
| GET | `/app/` | `StudentController.index` | — | Vista `spring-index` | `StudentController.java:21` |
| GET | `/app/students` | `StudentController.listStudents` | — | Vista `spring-index` | `StudentController.java:39` |
| GET | `/app/add-student` | `AddStudentController.showAddStudentForm` | — | Vista `spring-add-student` | `AddStudentController.java:22` |
| POST | `/app/add-student` | `AddStudentController.addStudent` | `name`, `email`, `major` (`@RequestParam`, form-urlencoded, obligatorios) | `redirect:/app/` con flash `successMessage` o `errorMessage` | `AddStudentController.java:27` |

### Observaciones
- `index` y `listStudents` son idénticos: mismo modelo, misma vista y código duplicado.
- No hay `@ResponseBody`: todos los endpoints devuelven un nombre de vista JSP.
- No hay `@Valid` / Bean Validation, `@ExceptionHandler` ni `@ControllerAdvice`.
- Inyección por campo con `@Autowired`.
- Los mensajes de error incluyen `e.getMessage()` y llegan al usuario (`StudentController.java:33`, `:50`; `AddStudentController.java:53`).
- `AddStudentController` importa `StudentProfile` y no lo usa (`AddStudentController.java:3`).

## Servlets Java EE (registrados en `web.xml`)

| Servlet | URL | Métodos | Dependencias | Salida | Archivo |
| --- | --- | --- | --- | --- | --- |
| IndexServlet | `/` (*default servlet*) | GET | MyBatisUtil (iBATIS) | Forward a `/index.jsp` | `src/org/sample/azure/student/coreft/IndexServlet.java` |
| AddStudentServlet | `/addStudent` | GET, POST | MyBatisUtil, JNDI `mail/StudentMailSession` | GET: forward a `/add_student_profile.jsp`. POST: HTML inline (éxito) o forward al formulario (error) | `src/org/sample/azure/student/coreft/AddStudentServlet.java` |
| StudentProfileListServlet | `/studentProfileList` | GET | MyBatisUtil, Jackson 1.x | HTML escrito con `PrintWriter` más un JSON escapado dentro del HTML | `src/org/sample/azure/student/coreft/StudentProfileListServlet.java` |

### Endpoints

| Método | URL | Handler | Parámetros | Salida | Evidencia |
| --- | --- | --- | --- | --- | --- |
| GET | `/` (y cualquier ruta sin mapeo) | `IndexServlet.doGet` | — | `index.jsp` con atributo `students` | `IndexServlet.java:22` |
| GET | `/addStudent` | `AddStudentServlet.doGet` | — | `add_student_profile.jsp` | `AddStudentServlet.java:27` |
| POST | `/addStudent` | `AddStudentServlet.doPost` | `name`, `email`, `major` (`getParameter`, opcionales) | HTML inline y envío de correo | `AddStudentServlet.java:34` |
| GET | `/studentProfileList` | `StudentProfileListServlet.doGet` | — | Tabla HTML y JSON (Content-Type `text/html`) | `StudentProfileListServlet.java:27` |

### Observaciones
- **Los servlets no usan Spring.** Llaman directamente a `MyBatisUtil` y se saltan `StudentService`, así que la lógica de datos está duplicada.
- `StudentProfileListServlet` escribe el error en la respuesta y luego lanza `RuntimeException` (`:58`): el cliente recibe un HTTP 500 con contenido parcial.
- `AddStudentServlet` guarda el error en el atributo `errorMsg` (`:90`), pero `add_student_profile.jsp` lee `errorMessage`. **El error nunca se muestra al usuario.**
- Un log de `AddStudentServlet` menciona un "HelloServlet" que no existe (`:83`), y el enlace "Add Another Student" apunta a `/` (`:87`).

## Solapamientos y rutas duplicadas

| Capacidad | Rutas servlet | Rutas Spring MVC | Diferencias |
| --- | --- | --- | --- |
| Listar estudiantes | `/`, `/studentProfileList` | `/app/`, `/app/students` | 4 rutas para la misma consulta. `/studentProfileList` además emite JSON. El manejo de errores es distinto en cada una |
| Alta de estudiante | `/addStudent` | `/app/add-student` | **Solo el servlet envía el correo de bienvenida.** El éxito se informa distinto (HTML inline vs redirect con flash). Con un parámetro faltante, la ruta Spring responde 400 y el servlet inserta `NULL` |

No hay `@RequestMapping` ambiguos dentro de Spring MVC. Los solapamientos están entre las dos pilas (servlets y `DispatcherServlet`), que se separan por el prefijo `/app/*`.

## Filtros

| Filtro | Registrado | Función | Archivo |
| --- | --- | --- | --- |
| CommonHttpServletFilter | **No**: no hay `<filter>` en `web.xml` ni `@WebFilter` | Lee `X-Forwarded-For` / `X-Client-IP` y guarda el atributo `RealClientIP` en el request. Ningún componente lee ese atributo | `src/org/sample/azure/student/coreft/filter/CommonHttpServletFilter.java` |

Si se reactiva, hay que tener en cuenta que esos headers los puede falsificar el cliente. Detrás de un proxy, la IP real debe resolverse con la configuración de *forwarded headers* de la plataforma, no confiando en el header.

## Vistas JSP

| JSP | Usada por | Líneas | Escapa la salida | Notas |
| --- | --- | --- | --- | --- |
| `WebContent/index.jsp` | IndexServlet | 137 | Sí, con `esc()` (línea 6) | Scriptlets |
| `WebContent/spring-index.jsp` | StudentController y el redirect tras el alta | 146 | Sí, con `esc()` (línea 6) | Scriptlets |
| `WebContent/spring-add-student.jsp` | AddStudentController (GET) | 85 | **No** para `successMessage`/`errorMessage` (líneas 43 y 49) | Formulario POST a `/app/add-student` (línea 53) |
| `WebContent/add_student_profile.jsp` | AddStudentServlet | 77 | **No** para `successMessage`/`errorMessage` (líneas 41 y 47) | Formulario POST a `/addStudent` (línea 51). El enlace a `/students` no tiene mapeo (línea 31) |

### Observaciones
- No hay JSTL ni taglibs. Toda la lógica de vista son scriptlets Java (`<% %>`, `<%! %>`).
- Los enlaces son absolutos (`/app/`, `/studentProfileList`) y asumen el context root `/`.
- La función de escape está triplicada: `esc()` en 2 JSP y `escapeHtml()` en `StudentProfileListServlet`.
- Las salidas sin escapar son hoy un riesgo XSS latente. En el flujo actual, el mensaje flash con el nombre del usuario se muestra en `spring-index.jsp`, que sí escapa.
- **Spring Boot no soporta JSP en un JAR ejecutable** (ver [blockers.md](../blockers.md), B1).
