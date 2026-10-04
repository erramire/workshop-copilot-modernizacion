# ADR-003: Migración `javax.*` → `jakarta.*` con OpenRewrite

| Campo | Valor |
| --- | --- |
| Estado | Aceptado |
| Fecha | 2026-10-04 |
| Relacionado | [B-01](../blockers.md#b-01), [B-07](../blockers.md#b-07), [javax-usages.md](../inventory/javax-usages.md), [ADR-004](ADR-004-upgrade-vs-greenfield.md) |

## Contexto

- Hay 24 imports a cambiar en 4 archivos (`javax.servlet` ×19, `javax.mail` ×5). `javax.naming` y `javax.sql` son de Java SE y no cambian.
- `web.xml` está en Servlet 4.0, y se compila contra las APIs `javax.servlet-api 4.0.1` y `javax.mail-api 1.6.2`.
- Spring 5.3 solo funciona con `javax.*` y Spring 6 solo con `jakarta.*`.
- OpenRewrite se ejecuta como plugin de Maven o Gradle y **no funciona sobre Ant**. Por eso el orden que propone el lab ("OpenRewrite → pom.xml") no se puede ejecutar.
- No hay código generado (JAXB, WSDL) que haya que regenerar.

## Decisión

1. **Primero, un `pom.xml` baseline** que compile el código legacy sin cambios: packaging `war`, `release 11`, dependencias equivalentes a `WEB-INF/lib` y las APIs `javax` como `provided`.
2. **Después, la transformación con OpenRewrite** (`rewrite-maven-plugin` 6.x), en una sola ejecución:

   | Receta | Artefacto | Propósito |
   | --- | --- | --- |
   | `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta` | `org.openrewrite.recipe:rewrite-migrate-java` | Cambio de namespace (obligatoria) |
   | `org.openrewrite.java.migrate.UpgradeToJava21` | `org.openrewrite.recipe:rewrite-migrate-java` | Java 21 ([ADR-001](ADR-001-java-target.md)) |
   | `org.openrewrite.java.logging.slf4j.Log4j1ToSlf4j1` | `org.openrewrite.recipe:rewrite-logging-frameworks` | log4j 1 → SLF4J ([ADR-007](ADR-007-cve-remediation.md)) |

3. **Validación:** `grep -rnE "import javax\.(servlet|mail|annotation|persistence|validation|transaction)" src/main/java` no debe devolver resultados.
4. **Atomicidad:** esta transformación y el salto a Spring Boot 3.5 (paso 3 del plan) van en el mismo commit. Entre ambos el build no compila.
5. Al terminar se quita el plugin de OpenRewrite del `pom.xml` y se deja constancia en `migration/migration-log.md`.

`web.xml` y las JSP no se corrigen a mano: se eliminan en el paso 5 ([ADR-009](ADR-009-web-layer-thymeleaf.md)).

## Alternativas consideradas

| Opción | Pros | Contras | Resultado |
| --- | --- | --- | --- |
| Migración manual (24 imports) | Viable por el tamaño | Propensa a errores; no muestra la herramienta del taller | Descartada |
| **OpenRewrite después de un pom baseline** | Repetible y auditable | Requiere crear el pom antes | **Elegida** |
| Shim en runtime (Eclipse Transformer) | No toca el código | Degrada el rendimiento y no es sostenible | Descartada |
| `UpgradeSpringBoot_3_5` como primera receta | Hace todo de una vez | El proyecto todavía no es Spring Boot | Descartada como primer paso |

## Consecuencias

- **Positivas:** el cambio es mecánico, reproducible y deja un diff que se puede revisar en el taller.
- **Negativas:**
  - Agrega un paso (el pom baseline).
  - El build queda roto entre los pasos 2 y 3.

## Validación

- El grep de la decisión no devuelve resultados.
- `./mvnw -B compile` pasa al terminar el paso 3.
