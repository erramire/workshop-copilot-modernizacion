# ADR-001: Java 21 (Eclipse Temurin)

| Campo | Valor |
| --- | --- |
| Estado | Aceptado |
| Fecha | 2026-10-04 |
| Relacionado | [ADR-002](ADR-002-spring-boot-version.md), [ADR-006](ADR-006-packaging.md), [ADR-012](ADR-012-config-observability.md) |

## Contexto

- El legacy compila con `source/target 11` (`build.xml`) y corre en Java 17 OpenJ9 (imagen de Open Liberty).
- Spring Boot 3.5 requiere al menos Java 17 y soporta hasta Java 25.
- Regla del taller: Eclipse Temurin 21, sin licencia Oracle. El devcontainer ya instala Temurin 21 (`JAVA_HOME_21`).
- El assessment no encontró APIs eliminadas entre Java 11 y 21: 0 usos de JAXB, CORBA y `sun.misc`.

## Decisión

- **Java 21 LTS** con **Eclipse Temurin** (HotSpot) en build y runtime.
- `java.version=21` en el `pom.xml`; el parent de Spring Boot lo traduce a `maven.compiler.release`.
- Imágenes `eclipse-temurin:21-jdk-alpine` para el build y `eclipse-temurin:21-jre-alpine` para el runtime ([ADR-006](ADR-006-packaging.md)).
- La receta de OpenRewrite `org.openrewrite.java.migrate.UpgradeToJava21` se aplica en el paso de transformación ([ADR-003](ADR-003-namespace-strategy.md)).
- **No** se habilitan virtual threads: no hay carga que lo justifique. Se pueden activar más adelante con `spring.threads.virtual.enabled=true`.

## Alternativas consideradas

| Opción | Pros | Contras | Resultado |
| --- | --- | --- | --- |
| Java 17 | Es el mínimo de Spring Boot 3 | Más antiguo; no cumple la regla del taller | Descartada |
| **Java 21** | LTS; cumple la regla del taller; el devcontainer ya lo trae | — | **Elegida** |
| Java 25 | LTS más reciente, soportado por Spring Boot 3.5 | Fuera de la regla del taller; cambian las imágenes y el devcontainer | Descartada (candidata a un upgrade posterior) |

## Consecuencias

- **Positivas:**
  - Versión LTS con soporte de largo plazo de Adoptium.
  - Permite usar records, pattern matching y text blocks.
  - Alineado con el devcontainer y con las imágenes del taller.
- **Negativas:**
  - Cambia la JVM (OpenJ9 → HotSpot): la memoria se ajusta con `-XX:MaxRAMPercentage` ([ADR-006](ADR-006-packaging.md)).
  - Java 21 advierte cuando un agente se carga dinámicamente (JEP 451). Afecta al agente de Application Insights ([ADR-012](ADR-012-config-observability.md)).

## Validación

- `./mvnw -v` y `java -version` reportan Temurin 21.
- El `pom.xml` no declara `source`/`target` sueltos; solo `java.version=21`.
