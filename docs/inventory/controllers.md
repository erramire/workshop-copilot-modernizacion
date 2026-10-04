# Inventario de controllers, servlets y vistas

Ruta base: `legacy/java/jakarta-ee/student-web-app/`. Paquete raíz: `org.sample.azure.student.coreft` (en `src/org/sample/azure/student/coreft/`).

La capa web es **híbrida**: 3 servlets clásicos declarados en `web.xml` y 2 controllers Spring MVC bajo `DispatcherServlet` (`/app/*`). No hay Struts.

## Mapa de URLs

| URL | Método | Handler | Tipo | Respuesta | Usa `StudentService` |
| --- | --- | --- | --- | --- | --- |
| `/` y cualquier URL no mapeada | GET | `IndexServlet.doGet` | Servlet | Forward a `/index.jsp` | No (`MyBatisUtil` directo) |
| `/addStudent` | GET | `AddStudentServlet.doGet` | Servlet | Forward a `/add_student_profile.jsp` | No |
| `/addStudent` | POST | `AddStudentServlet.doPost` | Servlet | HTML inline (éxito) o forward a la JSP (error) | No (`MyBatisUtil` + JNDI Mail) |
| `/studentProfileList` | GET | `StudentProfileListServlet.doGet` | Servlet | HTML generado + JSON escapado | No (`MyBatisUtil` directo) |
| `/app/` | GET | `StudentController.index` | `@Controller` | Vista `spring-index` | Sí |
| `/app/students` | GET | `StudentController.listStudents` | `@Controller` | Vista `spring-index` | Sí |
| `/app/add-student` | GET | `AddStudentController.showAddStudentForm` | `@Controller` | Vista `spring-add-student` | No |
| `/app/add-student` | POST | `AddStudentController.addStudent` | `@Controller` | `redirect:/app/` + flash attributes | Sí |
| `/*.jsp` | GET | Servlet JSP del contenedor | — | JSP renderizada sin datos | — |

- `IndexServlet` en `/` reemplaza al servlet por defecto: cualquier ruta inexistente (p. ej. `/students`) devuelve la página de inicio con HTTP 200. Como solo implementa `doGet`, un POST a una ruta no mapeada devuelve 405.
- No hay endpoints JSON (`@ResponseBody` / `@RestController`). La "REST API" que menciona la documentación del sample no existe.

## Spring MVC controllers

| Controller | URL base | Endpoints | Servicios usados | Archivo |
| --- | --- | --- | --- | --- |
| `StudentController` | `/app` | 2 (GET `/`, GET `/students`) | `StudentService` | `src/org/sample/azure/student/coreft/controller/StudentController.java` |
| `AddStudentController` | `/app` | 2 (GET `/add-student`, POST `/add-student`) | `StudentService` | `src/org/sample/azure/student/coreft/controller/AddStudentController.java` |

### StudentController

| Endpoint | Parámetros | Retorno | Modelo | Observaciones |
| --- | --- | --- | --- | --- |
| GET `/app/` → `index(Model)` | — | View name `spring-index` | `students`; `error` solo si hay excepción | El `catch` casi nunca se ejecuta: `StudentService` ya traga las excepciones |
| GET `/app/students` → `listStudents(Model)` | — | View name `spring-index` | Igual que `index` | **Duplicado** de `index` (misma lógica y vista) |

### AddStudentController

| Endpoint | Parámetros | Retorno | Observaciones |
| --- | --- | --- | --- |
| GET `/app/add-student` → `showAddStudentForm()` | — | View name `spring-add-student` | |
| POST `/app/add-student` → `addStudent(...)` | `@RequestParam` `name`, `email`, `major` (obligatorios) + `RedirectAttributes` | `redirect:/app/` | Flash `successMessage` ("Student {name} has been added successfully!") o `errorMessage`. Sin validación de formato ni longitud. **No envía email** |

En ambos controllers: inyección por campo (`@Autowired`), logging con log4j 1 y sin `@RequestMapping` a nivel de clase. No hay mappings ambiguos entre ellos, pero se solapan funcionalmente con los servlets.

## Servlets (`web.xml`)

| Servlet | URL | Métodos | Dependencias | Archivo |
| --- | --- | --- | --- | --- |
| `IndexServlet` | `/` | GET | `MyBatisUtil`, log4j | `src/org/sample/azure/student/coreft/IndexServlet.java` |
| `AddStudentServlet` | `/addStudent` | GET, POST | `MyBatisUtil`, JNDI `InitialContext`, JavaMail, log4j | `src/org/sample/azure/student/coreft/AddStudentServlet.java` |
| `StudentProfileListServlet` | `/studentProfileList` | GET | `MyBatisUtil`, `ObjectMapper` de Jackson 1, log4j | `src/org/sample/azure/student/coreft/StudentProfileListServlet.java` |
| `DispatcherServlet` (`spring`) | `/app/*` | — | `spring-servlet.xml` | Spring |

### Comportamiento relevante

- **IndexServlet:** consulta `listStudent` y hace forward a `index.jsp`. Si hay error, setea `error` con el mensaje de la excepción y `students=null`, y la tabla muestra "Loading student data...".
- **AddStudentServlet (POST):**
  1. Inserta con transacción manual y hace commit.
  2. Envía el email de bienvenida de forma síncrona. Si el envío falla, lo registra como "Error adding student", pero el alta ya está confirmada y se muestra la página de éxito.
  3. Si falla el insert, hace forward a `add_student_profile.jsp` con el atributo `errorMsg`. La JSP lee `errorMessage`, así que **el error no se muestra** (bug).
  4. La página de éxito es HTML inline; el link "Add Another Student" apunta a `/` (el listado), no al formulario.
- **StudentProfileListServlet:** genera la tabla HTML en Java y agrega al final la lista serializada con Jackson 1 (escapada como texto). Si hay error, escribe el mensaje y luego lanza `RuntimeException` con la respuesta ya parcialmente escrita.

## Filters

| Filter | Registrado | Función | Hallazgo |
| --- | --- | --- | --- |
| `CommonHttpServletFilter` | **No** (sin `<filter>` ni `@WebFilter`) | Guarda la IP del cliente (`X-Forwarded-For` → `X-Client-IP` → `remoteAddr`) en el atributo `RealClientIP` | Código muerto: nadie lee `RealClientIP`. Además confía en los headers sin validarlos |

## Struts actions

No aplica: no hay `struts.xml`, `struts-config.xml`, `ActionServlet` ni JARs de Struts.

## Vistas JSP

| JSP | La usa | LOC | Escapa la salida | Formulario | Observaciones |
| --- | --- | --- | --- | --- | --- |
| `WebContent/index.jsp` | `IndexServlet` | 137 | Sí (`esc()`) | — | Navegación con links absolutos |
| `WebContent/spring-index.jsp` | `StudentController` | 146 | Sí (`esc()`) | — | Muestra los flash attributes del alta |
| `WebContent/add_student_profile.jsp` | `AddStudentServlet` | 77 | **No** (`successMessage`, `errorMessage`) | POST `/addStudent` | Link roto `/students` (línea 31) |
| `WebContent/spring-add-student.jsp` | `AddStudentController` | 85 | **No** (`successMessage`, `errorMessage`) | POST `/app/add-student` | |

- Todas usan scriptlets (`<% %>`, `<%= %>`, `<%! %>`) y `request.getAttribute(...)`; no usan JSTL, EL ni taglibs.
- La función `esc()` está duplicada en 2 JSPs y en `StudentProfileListServlet`, y el CSS inline se repite en las 4.
- La validación es solo del lado del cliente (HTML5 `required`, `type="email"`).
- Las JSP están en la raíz de `WebContent/` (fuera de `WEB-INF`), así que se pueden abrir directamente por URL.
