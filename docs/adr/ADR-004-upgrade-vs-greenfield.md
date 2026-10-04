# ADR-004: Estrategia híbrida en `src/student-web-app/` y corte directo

| Campo | Valor |
| --- | --- |
| Estado | Aceptado |
| Fecha | 2026-10-04 |
| Relacionado | [B-05](../blockers.md#b-05), [B-16](../blockers.md#b-16), [riesgos](../risks.md) (R-02, R-10), [MIGRATION-SCOPE.md](../MIGRATION-SCOPE.md) |

## Contexto

- El lab sugiere un "upgrade in-place porque el proyecto tiene tests y estructura sana". El assessment encontró **0 tests**, lógica duplicada y código muerto.
- El código es chico (682 LOC Java + 445 LOC JSP), pero el target cambia el build, el empaquetado, el servidor, la persistencia, las vistas y el logging. En la práctica, casi todos los archivos se modifican o se eliminan.
- La regla global (`copilot-instructions.md`) dice que `legacy/` es **de solo lectura** para los agentes. `docs/playbook-referencia.md` contempla una excepción in-place para Java que contradice esa regla.
- `legacy/java/` es un clon con su propio `.git`, que apunta a Azure-Samples. Por eso el push del Paso 7 del lab fallaría.
- La línea base legacy no se puede ejecutar en el Codespace porque falta Ant ([B-05](../blockers.md#b-05)), así que conviene conservarla intacta como referencia.

## Decisión

- **Estrategia híbrida:** un proyecto Maven nuevo en **`src/student-web-app/`** que parte de una copia del código legacy. Esa copia se transforma con OpenRewrite ([ADR-003](ADR-003-namespace-strategy.md)) y después se refactoriza por feature ([migration-plan.md](../migration-plan.md)).
- `legacy/` no se modifica y no se crea `legacy.original/`.
- El paquete raíz pasa a ser `org.sample.azure.student` (se quita el segmento `coreft`).
- Instrucción para `@spring-legacy-migration`: seguir su ruta **greenfield** (estructura en `src/student-web-app/`), pero partiendo del código copiado en lugar de un esqueleto vacío.
- **Corte (cutover) directo:**
  - La app nueva es la única que se despliega en Azure; el legacy no se publica.
  - Para volver atrás se reactiva la revisión anterior de la Container App o se redespliega el tag de imagen previo.
  - No se usa Strangler Fig: son 3 features y en el taller no hay tráfico productivo ni base de datos compartida.

## Alternativas consideradas

| Opción | Pros | Contras | Resultado |
| --- | --- | --- | --- |
| In-place en `legacy/java/jakarta-ee/student-web-app/` | Es lo que sugiere el lab | Rompe la regla de solo lectura; mezcla cambios con un `.git` ajeno; sin tests no da más seguridad | Descartada |
| Greenfield desde cero | Diseño limpio | Se pierde la trazabilidad y el ejercicio con OpenRewrite | Descartada |
| **Híbrida** | Trazabilidad y legacy intacto | Hay que ajustar rutas en los labs | **Elegida** |
| Strangler Fig | Migración gradual con tráfico real | Proxy y sincronización innecesarios para 3 features | Descartada |

## Consecuencias

- **Positivas:**
  - El legacy queda disponible como referencia.
  - El diff es claro.
  - Se cumple la regla global.
- **Negativas:**
  - Hay que ajustar comandos de los labs 02 y 03 (la lista está en [migration-plan.md](../migration-plan.md#ajustes-requeridos-al-material-del-taller)).
  - El Dockerfile pasa de `legacy/java/Dockerfile` a `src/student-web-app/Dockerfile`.

## Validación

- Al terminar la Fase 3, `git -C legacy/java status --porcelain` no devuelve cambios.
- `src/student-web-app/` compila y se ejecuta por sí solo.
