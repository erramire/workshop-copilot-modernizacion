# ADR-002: Estrategia de migración: proyecto nuevo en `src/` a partir del código legacy (híbrido)

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisión 2 del planning, aceptada en el chat)

> **Resumen para `@spring-legacy-migration`**
> - Estrategia: **híbrido**. En el agente equivale a la ruta greenfield, pero partiendo de una copia del código legacy.
> - Spring target: Spring Boot 3.5.16 ([ADR-001](ADR-001-stack-target.md))
> - Java target: 21 (Eclipse Temurin)
> - Ruta del proyecto: `src/student-web-app/`
> - `legacy/` no se modifica, así que no hace falta la copia `legacy.original/`.

## Contexto
- Según `.github/copilot-instructions.md`, el código de `legacy/` es de solo lectura.
- `legacy/java` es un clon del repositorio upstream `Azure-Samples/java-migration-copilot-samples`. Lo que se commitee ahí no pertenece al repositorio del taller.
- La estructura legacy no es Maven: `src/` en la raíz, `WebContent/` y JARs dentro de `WEB-INF/lib`.
- No hay tests ([blockers.md](../blockers.md), QA-01). El código es pequeño (unas 680 líneas Java) y la mayor parte hay que reescribirla de todos modos: persistencia (iBATIS), vistas (JSP) y servlets.
- Las reglas del taller piden aplicar OpenRewrite como primer paso del cambio de namespace.

## Opciones consideradas
1. **Upgrade in-place en `legacy/`.** Es lo que propone el lab.
   - En contra: rompe la regla de solo lectura, mezcla cambios con un clon de otro repositorio y obliga a reestructurar Ant a Maven en el sitio.
2. **Greenfield desde cero en `src/`.**
   - A favor: estructura limpia.
   - En contra: descarta el código reutilizable y elimina el paso de OpenRewrite que pide el taller.
3. **Híbrido.** Proyecto Maven nuevo en `src/student-web-app/`. Se copia el código Java legacy como línea base, se pasa OpenRewrite sobre la copia y después se refactoriza feature por feature.

## Decisión
Opción 3:
- El proyecto vive en `src/student-web-app/` con el layout estándar de Maven.
- Paquete base `org.sample.azure.student`, con los subpaquetes `config`, `domain`, `application`, `infrastructure.persistence`, `infrastructure.mail` y `presentation`.
- La línea base son los 9 archivos `.java` del legacy, copiados tal cual. Los XML, las JSP y los archivos de Ant no se copian.
- Cada elemento legacy tiene asignada una estrategia (copiar, refactorizar, reescribir o eliminar) en la sección "Estrategia por módulo" de [migration-plan.md](../migration-plan.md).
- Cada paso del plan termina con el build en verde y una entrada en `migration/migration-log.md`.

## Consecuencias
- **Positivas:** respeta la regla de solo lectura y es totalmente reversible: basta con borrar `src/student-web-app/`. Además conserva OpenRewrite como primer paso.
- **Negativas:** los labs 02 y 03 apuntan a `legacy/java/`, así que hay que actualizar rutas. Los cambios están listados en la sección de handoff de [migration-plan.md](../migration-plan.md).
- **Riesgos a monitorear:** R-09 y R-13 en [risks.md](../risks.md).

## Referencias
- [assessment-summary.md](../assessment-summary.md) y [blockers.md](../blockers.md)
- [MIGRATION-SCOPE.md](../MIGRATION-SCOPE.md)
