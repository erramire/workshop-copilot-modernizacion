# ADR-012: Estrategia de pruebas

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisiones por defecto del planning, aceptadas en el chat)

## Contexto
- El legacy no tiene ningún test (QA-01).
- La línea base legacy tampoco se puede ejecutar de forma fiable, porque al classpath le faltan dependencias (PLT-04). Por eso no hay contra qué escribir tests de caracterización en runtime.
- El comportamiento esperado está acordado por escrito en [MIGRATION-SCOPE.md](../MIGRATION-SCOPE.md), con criterios de aceptación AC-01 a AC-21.
- La base de datos del taller es H2 en modo MySQL ([ADR-005](ADR-005-base-de-datos.md)), así que Testcontainers no es imprescindible.

## Opciones consideradas
1. **JUnit 5 con MockMvc y `@DataJpaTest` sobre H2, más tests unitarios con Mockito.** Cada criterio de aceptación tiene al menos un test.
2. **Lo mismo, más Testcontainers con MySQL como obligatorio.**
   - En contra: necesita Docker en cada build y valida un motor que el taller no usa.
3. **Tests de caracterización contra el legacy en ejecución.**
   - En contra: el legacy no arranca de forma fiable (PLT-04) y exige Liberty y MySQL.

## Decisión
Opción 1.

| Nivel | Herramienta | Qué cubre |
| --- | --- | --- |
| Repositorio | `@DataJpaTest` con `@AutoConfigureTestDatabase(replace = NONE)`, para usar la misma URL H2 en modo MySQL y Flyway | Esquema V1, orden por id e id generado |
| Service | JUnit 5 y Mockito, sin contexto de Spring | Alta, listado y errores propagados |
| Notificación | JUnit 5 y Mockito, con un `JavaMailSender` simulado | AC-12, AC-13 y AC-14 |
| Web | `@WebMvcTest` con `spring-security-test` (`csrf()`) e importando la configuración de seguridad | Validación, redirecciones, CSRF y mensajes |
| Aceptación | `@SpringBootTest` con `@AutoConfigureMockMvc` | Contrato de URLs de punta a punta y `/health` |

### Reglas
- Los tests de cada feature se escriben **antes** de portarla, a partir de los criterios de aceptación. El nombre o el `@DisplayName` de cada test incluye el id del criterio (por ejemplo, `AC-07`).
- Cada paso de [migration-plan.md](../migration-plan.md) termina con `./mvnw -B verify` en verde.
- Criterio de salida: cada criterio de aceptación tiene al menos un test.
- Testcontainers con MySQL es opcional y queda como tarea previa al uso de MySQL en un entorno real (R-04).

## Consecuencias
- **Positivas:**
  - Hay red de seguridad desde la primera línea portada.
  - Los tests son rápidos y no necesitan Docker.
  - Se puede trazar cada test hasta su criterio de aceptación.
- **Negativas:** la compatibilidad con MySQL no se valida en el taller.
- **Riesgos a monitorear:** R-03 y R-04 en [risks.md](../risks.md).

## Referencias
- [MIGRATION-SCOPE.md](../MIGRATION-SCOPE.md) y [blockers.md](../blockers.md) (QA-01)
