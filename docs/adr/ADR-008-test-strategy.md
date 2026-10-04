# ADR-008: Estrategia de pruebas

| Campo | Valor |
| --- | --- |
| Estado | Aceptado |
| Fecha | 2026-10-04 |
| Relacionado | [B-05](../blockers.md#b-05), [B-16](../blockers.md#b-16), [riesgos](../risks.md) (R-02, R-03), [matriz de paridad](../MIGRATION-SCOPE.md#matriz-de-paridad) |

## Contexto

- El legacy tiene 0 tests, y su línea base no se puede ejecutar en el Codespace porque falta Ant.
- El comportamiento esperado está documentado en `docs/features/` y en la matriz de paridad de `MIGRATION-SCOPE.md`.
- El devcontainer tiene Docker (docker-in-docker), así que se puede usar Testcontainers.

## Decisión

- **Stack:**
  - JUnit 5, AssertJ y Mockito (`spring-boot-starter-test`).
  - `spring-security-test`.
  - Testcontainers (`spring-boot-testcontainers`, `org.testcontainers:mysql`) con `@ServiceConnection`.
- **Pruebas por capa:**

| Capa | Tipo | Herramienta | Qué cubre |
| --- | --- | --- | --- |
| Repositorio | `@DataJpaTest` | Testcontainers MySQL 8.4 con `@AutoConfigureTestDatabase(replace = NONE)` | Flyway V1, `ddl-auto=validate`, orden por id |
| Servicio | Unitario | Mockito | `register`, `findAll` y la publicación del evento |
| Notificación | Integración | `@SpringBootTest` (H2) | Se notifica después del commit; si el notificador falla, el alta no se revierte; `SmtpWelcomeNotifier` con `JavaMailSender` mockeado (mismo asunto y cuerpo que el legacy) |
| Web | `@WebMvcTest` | MockMvc + `spring-security-test` | Rutas, PRG, errores de validación por campo, 301 de URLs legacy, 404, CSRF (403 sin token), headers de seguridad y escapado de HTML |
| Smoke | `@SpringBootTest(webEnvironment = RANDOM_PORT)` | H2 | El contexto arranca y `/actuator/health` responde UP |

- **Caracterización:** cada fila de la matriz de paridad (P-01 a P-19) tiene al menos un test, tanto si el comportamiento se conserva como si cambia a propósito.
- Los tests se escriben **en el mismo paso** que el código que cubren ([migration-plan.md](../migration-plan.md)).
- `./mvnw -B verify` corre todo; los tests de repositorio necesitan Docker para Testcontainers.

## Alternativas consideradas

| Opción | Pros | Contras | Resultado |
| --- | --- | --- | --- |
| Solo H2 en los tests | Rápido y sin Docker | No valida el motor real | Descartada para el repositorio |
| Caracterización contra el legacy en ejecución | Compara con el sistema real | El legacy no corre en Codespaces | Descartada (se usa la especificación) |
| GreenMail para SMTP | Prueba un SMTP real | Una dependencia más | Opcional |

## Consecuencias

- **Positivas:**
  - Hay red de seguridad desde el primer paso.
  - La paridad queda documentada en forma de tests ejecutables.
- **Negativas:** `verify` necesita Docker, y la primera vez descarga la imagen `mysql:8.4`.

## Validación

- `./mvnw -B verify` pasa, y los reportes de Surefire incluyen tests de las 5 capas.
- `migration/parity-notes.md` relaciona cada P-xx con su test.
