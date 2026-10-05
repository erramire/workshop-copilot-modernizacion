# ADR-007: Capa web: de servlets clásicos a Spring MVC, y contrato de URLs

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisiones 6 y 8 del planning, aceptadas en el chat)

> No hay Struts. Este ADR ocupa la posición que `@spring-legacy-migration` reserva para Struts y cubre la salida de los servlets clásicos.

## Contexto
- El legacy es híbrido (FUN-01, FUN-02, FUN-03):
  - 3 servlets fuera de Spring: `/`, `/addStudent` y `/studentProfileList`.
  - 2 controllers Spring MVC bajo `/app/*`.
  - En total hay 4 URLs de listado y 2 flujos de alta que se comportan distinto.
- La configuración está en `web.xml`, `applicationContext.xml` y `spring-servlet.xml`. `applicationContext-service.xml` es huérfano.
- Spring 6 desactiva la coincidencia con barra final y usa `PathPatternParser`.

## Opciones consideradas
1. **Un solo conjunto de controllers Spring MVC.** Las URLs oficiales son las actuales de Spring MVC (`/app/*`) y las de los servlets redirigen a ellas.
2. **Mantener las dos familias de URLs**, cada una con su handler: paridad total, pero duplicando código.
3. **Eliminar las URLs legacy:** rompe enlaces y marcadores existentes.

## Decisión
Opción 1.

- **`presentation.StudentController`**, con `@RequestMapping("/app")`:
  - `GET ""`, `"/"` y `"/students"`: listado, con la vista `students/list`.
  - `GET "/add-student"`: formulario, con la vista `students/form`.
  - `POST "/add-student"`: valida `StudentForm` con `@Valid`.
    - Si hay errores, vuelve a mostrar el formulario con estado 200.
    - Si no, llama a `StudentService.register`, deja el mensaje flash `successMessage` y redirige a `/app/` (patrón PRG).
- **`presentation.LegacyRedirectController`**, con redirecciones **302**:
  - `GET /` a `/app/`.
  - `GET /addStudent` a `/app/add-student`.
  - `GET /studentProfileList` a `/app/students`.
  - Se usa 302 (temporal) para poder hacer rollback sin que lo impidan las cachés de los navegadores.
- **`POST /addStudent` se retira:** su único origen era el formulario legacy, que desaparece.
- **`presentation.GlobalExceptionHandler`** (`@ControllerAdvice`): muestra mensajes genéricos al usuario y deja el error completo solo en el log.
- Inyección por constructor en todos los controllers.
- No se acepta barra final salvo en `/app/`. Las URLs desconocidas devuelven 404.
- El JSON que `/studentProfileList` incrustaba en el HTML desaparece: no era una API, y la decisión 8 descarta añadir una.
- Los 3 servlets, el filtro `CommonHttpServletFilter` y los 2 controllers legacy se eliminan en cuanto sus sustitutos tienen tests (pasos 3 y 4 de [migration-plan.md](../migration-plan.md)).

### Configuración XML que desaparece

| Legacy | Spring Boot 3.5 |
| --- | --- |
| `ContextLoaderListener` y `DispatcherServlet` en `/app/*` | `DispatcherServlet` autoconfigurado en `/` |
| 3 `context:component-scan` | Escaneo de componentes desde `StudentWebApplication` |
| `mvc:annotation-driven` | `WebMvcAutoConfiguration` |
| `InternalResourceViewResolver` (JSP) | Thymeleaf autoconfigurado ([ADR-008](ADR-008-frontend.md)) |
| `mvc:default-servlet-handler` | Recursos estáticos en `classpath:/static` |
| `resource-ref` JNDI | `spring.datasource.*` y `spring.mail.*` |
| `applicationContext-service.xml` (huérfano) | Se elimina |

El contrato de URLs completo y los criterios de aceptación están en [MIGRATION-SCOPE.md](../MIGRATION-SCOPE.md).

## Consecuencias
- **Positivas:**
  - Un solo comportamiento por caso de uso.
  - Los enlaces legacy siguen funcionando.
  - Desaparece el código duplicado y el código muerto.
- **Negativas:** hay cambios visibles: redirecciones, 404, `POST /addStudent` retirado y `/studentProfileList` sin JSON.
- **Riesgos a monitorear:** R-08 y R-11 en [risks.md](../risks.md).

## Referencias
- [inventory/controllers.md](../inventory/controllers.md) y [inventory/spring-config.md](../inventory/spring-config.md)
- [features/01-consulta-estudiantes.md](../features/01-consulta-estudiantes.md) y [features/02-registro-estudiante.md](../features/02-registro-estudiante.md)
