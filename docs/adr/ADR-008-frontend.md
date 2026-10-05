# ADR-008: Frontend: Thymeleaf en lugar de JSP

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisión 7 del planning, aceptada en el chat)

## Contexto
- Hay 4 JSP con scriptlets y sin JSTL.
- El escape HTML está copiado a mano en varias JSP, y las dos JSP de formulario no escapan la salida (SEC-09).
- Spring Boot no soporta JSP en un JAR ejecutable (PLT-02).
- El lab pide no cambiar la UI.

## Opciones consideradas
1. **Thymeleaf replicando el HTML actual.**
   - A favor: compatible con JAR, escapa la salida por defecto y la UI no cambia.
2. **JSP empaquetadas en un WAR.**
   - En contra: incumple la regla del JAR y mantiene los scriptlets.
3. **SPA con una API REST.**
   - En contra: sobredimensionado para 2 pantallas y cambia la UI.

## Decisión
Opción 1.

### Plantillas

| Legacy | Destino |
| --- | --- |
| `spring-index.jsp` e `index.jsp` | `templates/students/list.html` |
| `spring-add-student.jsp` y `add_student_profile.jsp` | `templates/students/form.html` |
| — | `templates/error.html`, página genérica para 404 y 500 |
| Cabecera y navegación repetidas en cada JSP | `templates/fragments/layout.html` |

### Reglas
- Los estilos inline de las JSP pasan a `static/css/site.css`, con el mismo aspecto: colores, tipografía, tabla y formulario.
- La salida usa siempre `th:text`, que escapa automáticamente. No se usa `th:utext`. Con esto sobran las funciones `esc()` y `escapeHtml()`.
- La lógica de los scriptlets (castings, comprobaciones de null y elección de mensajes) pasa a los controllers antes de escribir las plantillas.
- Los enlaces se construyen con `@{...}`, relativos al contexto.
- Los formularios usan `th:action`, así Spring Security añade el token CSRF automáticamente ([ADR-009](ADR-009-seguridad.md)).
- Los errores de validación se muestran por campo (`th:errors`) y se conserva lo que el usuario había escrito.

### Textos de la UI
- Siguen en inglés, como en el legacy. Se mantienen las etiquetas, los placeholders, las columnas y los mensajes.
- El botón del formulario pasa a "Add Student".
- Se eliminan los bloques informativos sobre el stack ("Powered by Spring Framework 5.3 and Open Liberty", "Technology Stack", "Available Endpoints") y los enlaces a URLs legacy.
- La navegación se reduce a "Students" y "Add Student".

## Consecuencias
- **Positivas:**
  - Compatible con el JAR ejecutable.
  - Corrige el XSS latente.
  - Una plantilla por pantalla, en vez de dos JSP casi iguales.
- **Negativas:** cambios menores en los textos y la navegación, documentados en [MIGRATION-SCOPE.md](../MIGRATION-SCOPE.md).
- **Riesgos a monitorear:** R-08 en [risks.md](../risks.md).

## Referencias
- [inventory/controllers.md](../inventory/controllers.md), sección "Vistas JSP"
