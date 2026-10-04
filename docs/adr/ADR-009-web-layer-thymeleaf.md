# ADR-009: Capa web con Spring MVC + Thymeleaf y un solo camino por caso de uso

| Campo | Valor |
| --- | --- |
| Estado | Aceptado |
| Fecha | 2026-10-04 |
| Relacionado | [B-03](../blockers.md#b-03), [B-16](../blockers.md#b-16), [controllers.md](../inventory/controllers.md), [ADR-006](ADR-006-packaging.md), [ADR-011](ADR-011-security-baseline.md) |

## Contexto

- La capa web mezcla 3 servlets (declarados en `web.xml`), 2 controllers de Spring MVC y 4 JSP con scriptlets. Cada caso de uso tiene 2 o 3 implementaciones que se comportan distinto.
- Hay bugs:
  - El servlet guarda el error en el atributo `errorMsg`, pero la JSP lee `errorMessage`.
  - Hay un link roto a `/students`.
  - `/app/` duplica a `/app/students`.
  - `IndexServlet` atiende cualquier URL (catch-all).
- Las JSP no funcionan en un JAR ejecutable ([ADR-006](ADR-006-packaging.md)), y 2 de ellas imprimen valores sin escapar.
- No hay API REST: `/studentProfileList` solo incrusta un JSON de demostración.
- Spring 6 ya no hace *trailing slash matching* por defecto.
- No hay Struts.

## Decisión

- **Un solo controller, `StudentController`**, con inyección por constructor:
  - `GET /` → 302 a `/students`.
  - `GET /students` → vista `students/list`.
  - `GET /students/new` → vista `students/form`.
  - `POST /students`: si los datos son válidos, 302 a `/students` con el flash `successMessage` (PRG); si no, vuelve a mostrar el formulario con los errores por campo.
- **`LegacyRedirectController`** responde con 301:
  - `GET /studentProfileList`, `/app`, `/app/` y `/app/students` → `/students`.
  - `GET /addStudent` y `/app/add-student` → `/students/new`.
  - Los POST a URLs legacy no se soportan.
  - `/app` y `/app/` se mapean por separado, porque Spring 6 no iguala la barra final.
- **Thymeleaf 3.1** (`spring-boot-starter-thymeleaf`): plantillas `students/list.html`, `students/form.html` y `error.html`, con un fragmento de layout común.
  - Se conservan los textos, la estructura HTML y los estilos (paridad visual).
  - El CSS pasa a `static/css/app.css`. No quedan atributos `style` ni `<script>` inline, para que funcione la CSP ([ADR-011](ADR-011-security-baseline.md)).
  - Los valores se imprimen solo con `th:text`, que escapa automáticamente. Los formularios usan `th:action`, que agrega el token CSRF, y los errores se muestran con `th:errors`.
  - Los links usan `@{...}`, sin rutas absolutas escritas a mano.
- **Validación:** `StudentForm` con Bean Validation (`spring-boot-starter-validation`):
  - `@NotBlank` y `@Size(max = 255)` en los 3 campos.
  - `@Email` en `email`.
- **Sin API REST** y sin el JSON incrustado.
- Las rutas desconocidas devuelven 404 con un `error.html` genérico.
- **Se eliminan:**
  - Los servlets `IndexServlet`, `AddStudentServlet` y `StudentProfileListServlet`.
  - `CommonHttpServletFilter` y los 2 controllers legacy.
  - Las 4 JSP.
  - `web.xml`, `applicationContext.xml`, `spring-servlet.xml` y `applicationContext-service.xml`.

## Alternativas consideradas

| Opción | Pros | Contras | Resultado |
| --- | --- | --- | --- |
| Mantener los servlets con `ServletRegistrationBean` | Menos reescritura | Perpetúa la duplicidad y los bugs | Descartada |
| SPA + API REST | UI moderna | Fuera de alcance; agrega piezas | Descartada |
| JSP con WAR ejecutable | No hay que reescribir vistas | Contradice [ADR-006](ADR-006-packaging.md) | Descartada |
| **Thymeleaf + un solo controller** | Paridad visual y seguro por defecto | Hay que reescribir 4 vistas (pequeñas) | **Elegida** |

## Consecuencias

- **Positivas:**
  - Cada caso de uso tiene un solo comportamiento.
  - Se corrigen los bugs y el XSS queda mitigado por diseño.
- **Negativas:**
  - Las URLs cambian (se mitiga con los 301).
  - Los POST a URLs legacy dejan de funcionar.
  - Las rutas desconocidas devuelven 404 en lugar de la portada.

## Validación

- Hay tests de MockMvc para cada ruta, incluidos los 301 y el 404.
- `grep -r "<%" src/main` no devuelve nada, y no quedan `web.xml` ni XML de Spring.
