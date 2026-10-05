# Bloqueos e incidencias no previstas: student-web-app

> Ejecuta `@spring-legacy-migration` · 2026-10-04
>
> Según [migration-plan.md](../docs/migration-plan.md), todo lo que el plan no contemplaba se apunta aquí y se escala. No se tomaron decisiones de diseño nuevas.

**Estado:** no hay ningún bloqueo abierto que impida pasar a la Fase 4.

| ID | Paso | Incidencia | Impacto | Estado | Para quién |
| --- | --- | --- | --- | --- | --- |
| BF-01 | 1 | Además de los imports, OpenRewrite cambió el `pom.xml`: `jakarta.servlet-api` y `jakarta.mail-api` con scope compile, y `log4j-api` y `log4j-core` 2.26.1 | `log4j-core` habría entrado en conflicto con Logback (`spring-boot-starter-logging`) | **Resuelto.** Se revirtió a mano y se comprobó con `dependency:tree` (AC-21) | Facilitador: avisar en el lab 02 de que hay que revisar el `pom.xml` después de ejecutar la receta |
| BF-02 | 5 | Con `app.mail.welcome.enabled=true` y sin `SPRING_MAIL_HOST`, la aplicación no arranca porque falta el bean `JavaMailSender`. ADR-011 no lo contempla | En Azure, una configuración incompleta deja el contenedor sin arrancar | **Abierto, informativo.** Se mantiene como fallo rápido y explícito | `@azure-architect`: si se activa el email, definir `SPRING_MAIL_HOST` y pasar las credenciales SMTP como secretos de Container Apps |
| BF-03 | 7 | El escaneo OWASP no se ha ejecutado: el Codespace no tiene `NVD_API_KEY` | Sin escaneo con herramienta de CVEs (R-01, SEC-08) | **Abierto.** El perfil `security-scan` está listo (`dependency-check-maven` 13.0.0, falla con CVSS ≥ 7) y comprobado con el effective POM. Dependabot está activo | Quien tenga una clave de NVD: `NVD_API_KEY=… ./mvnw -B -Psecurity-scan verify` |
| BF-04 | 7 | [ADR-012](../docs/adr/ADR-012-pruebas.md) pide al menos un test por criterio, pero el plan verifica AC-19 a AC-21 con comandos (pasos 6 y 7). Además, un test JUnit para AC-21 tendría que contener los nombres prohibidos y haría fallar el propio `grep` del plan | Trazabilidad de AC-19 a AC-21 | **Abierto.** La verificación por comandos está documentada en [parity-notes.md](parity-notes.md) | Revisor de la Fase 2: decidir si se automatiza, por ejemplo en CI, y cómo |
| BF-05 | 6 | Ya existe `applicationinsights-agent` 3.7.10, pero ADR-004 fija la 3.7.9 | Ninguno | **Informativo.** Dependabot lo detectará porque analiza los `artifactItems` de `maven-dependency-plugin` | Mantenimiento |

## Riesgos confirmados durante la ejecución
- **R-10:** con el agente de Application Insights, el arranque pasa de 9,8 s a 18,8 s (paso 6).

## Pendientes previstos (handoff, fuera de alcance)
No son bloqueos no previstos. Se repiten aquí para que no se pierdan; el detalle está en [migration-plan.md](../docs/migration-plan.md).

- `labs/lab-03-iac/README.md`, paso 8: la imagen se sigue construyendo desde `legacy/java/`. Hay que cambiarlo a `src/student-web-app/` y añadir la etiqueta inmutable (ADR-013). **Si no se cambia, el lab 03 despliega el legacy.**
- `infra/main.bicep` (`petclinicApp`): faltan las probes `/health/liveness` y `/health/readiness` y `maxReplicas: 1` (R-05 y R-11).
- `labs/lab-02-java/README.md`: rutas, nombre de la aplicación (no es PetClinic) y numeración de los ADRs.
