# ADR-013: Cutover y rollback en Azure Container Apps

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisiones 13 y 14 del planning, aceptadas en el chat)

## Contexto
- El legacy no está desplegado en Azure: corre en local, con Liberty y docker-compose. No hay tráfico productivo que tenga que convivir con la app nueva.
- El Bicep ya crea la Container App `ca-petclinic-{participantPrefix}` (puerto 8080, perfil `prod`, de 0 a 3 réplicas, sin probes), y el lab 03 publica la imagen `petclinic:workshop`.
- En el taller no se migran datos: la base de datos es H2 y arranca vacía ([ADR-005](ADR-005-base-de-datos.md)).

## Opciones consideradas
1. **Cambio directo usando revisiones de Container Apps.** Se despliega la imagen nueva, se valida, y para hacer rollback se vuelve a la imagen o revisión anterior.
2. **Strangler Fig, con un proxy delante del legacy y de la app nueva.**
   - En contra: no aporta nada, porque no hay legacy en Azure y la app es mínima.
3. **Blue-green con dos Container Apps.**
   - En contra: duplica infraestructura cuando las revisiones de Container Apps ya resuelven el problema.

## Decisión
Opción 1, manteniendo los nombres actuales (decisión 14).

### Despliegue (Fase 4, lab 03)
1. Construir la imagen desde `src/student-web-app/`.
2. Etiquetarla dos veces: `petclinic:workshop` y una etiqueta inmutable, `petclinic:<git-sha>`.
3. Publicar ambas etiquetas en el ACR.
4. Ejecutar `az containerapp update --image <acr>/petclinic:<git-sha>` sobre `ca-petclinic-{participantPrefix}`, lo que crea una revisión nueva.

### Validación
- `/health/readiness` responde `UP`.
- El listado `/app/students` carga.
- El alta funciona de punta a punta, incluido el mensaje de éxito.
- Las redirecciones legacy funcionan.

### Rollback
Volver a desplegar la etiqueta inmutable anterior, o reactivar la revisión anterior si la Container App está en modo de varias revisiones.

### Datos
- En el taller no hay migración de datos.
- Si se adopta una base MySQL legacy, se usa el baseline de Flyway ([ADR-005](ADR-005-base-de-datos.md)). El esquema es compatible, así que la app nueva y el legacy podrían apuntar a la misma base.

### Legacy
Se queda en `legacy/` como referencia. No se despliega ni se apaga, porque no está en Azure.

### Cambios que necesita la Fase 4
Las detalla la sección de handoff de [migration-plan.md](../migration-plan.md):
- Ruta del build.
- Probes `/health/liveness` y `/health/readiness`.
- `maxReplicas: 1`.

## Consecuencias
- **Positivas:**
  - El procedimiento es simple y reversible.
  - No se toca la estructura del Bicep, solo se añaden probes y el límite de réplicas.
- **Negativas:** si no se fija la etiqueta inmutable, `petclinic:workshop` sola no permite volver atrás.
- **Riesgos a monitorear:** R-05 y R-09 en [risks.md](../risks.md).

## Referencias
- `infra/main.bicep` y `labs/lab-03-iac/README.md` (paso 8)
