# ADR-002: Spring Boot 3.5.x

| Campo | Valor |
| --- | --- |
| Estado | Aceptado, con mitigación obligatoria |
| Fecha | 2026-10-04 |
| Relacionado | [B-06](../blockers.md#b-06), [riesgos](../risks.md) (R-01), [ADR-001](ADR-001-java-target.md), [ADR-007](ADR-007-cve-remediation.md) |

## Contexto

- El legacy usa Spring Framework 5.3.23 sin Spring Boot. La rama 5.3 dejó de recibir soporte OSS el 31-ago-2024.
- El taller (`copilot-instructions.md`) y el usuario piden **Spring Boot 3.x** con JAR ejecutable.
- Estado de soporte al 2026-10-04 ([endoflife.date](https://endoflife.date/spring-boot); releases verificados en Maven Central):

| Línea | Último release OSS | Soporte OSS | Soporte comercial |
| --- | --- | --- | --- |
| 3.5 (Spring Framework 6.2) | 3.5.16 (25-jun-2026) | Terminó el 30-jun-2026 | Hasta el 30-jun-2032 |
| 4.0 (Spring Framework 7.0) | 4.0.x | Hasta el 31-dic-2026 | Hasta el 31-dic-2027 |
| 4.1 (Spring Framework 7.0) | 4.1.x | Hasta el 31-jul-2027 | Hasta el 31-jul-2028 |

## Decisión

- Usar **Spring Boot 3.5.16** (`spring-boot-starter-parent`). Es el último release OSS de la línea 3.5: no existe 3.5.17 en Maven Central.
- El BOM gestiona el resto del stack: Spring Framework 6.2, Jakarta EE 10 (Servlet 6.0), Tomcat 10.1, Hibernate ORM 6.6, Thymeleaf 3.1 y Spring Security 6.5.
- **Mitigación obligatoria**, porque la línea 3.5 ya no recibe parches OSS:
  1. Antes de pasar a producción, contratar soporte comercial (Tanzu Spring) **o** planificar el upgrade a Spring Boot 4.x apenas termine el taller. OpenRewrite tiene recetas para Spring Boot 4.
  2. Escanear CVEs en cada build de CI ([ADR-007](ADR-007-cve-remediation.md)).

## Alternativas consideradas

| Opción | Pros | Contras | Resultado |
| --- | --- | --- | --- |
| **Spring Boot 3.5.16** | Cumple el requisito; documentación y recetas maduras | Sin parches OSS desde el primer día | **Elegida** |
| Spring Boot 4.1 | Soporte OSS hasta jul-2027; Jakarta EE 11 | No cumple "Spring Boot 3"; usa Jackson 3 por defecto y trae más cambios | Descartada (es el siguiente paso recomendado) |
| Spring Boot 3.4 | — | Su soporte comercial termina en dic-2026 | Descartada |
| Quarkus | Arranque más rápido | Hay que reescribir anotaciones y configuración | Descartada |
| Spring Framework 6.2 sin Boot | El salto es menor | No hay JAR ejecutable ni auto-configuración | Descartada |

## Consecuencias

- **Positivas:**
  - Cumple el requisito del taller.
  - Los ADRs y el plan sirven también para el salto a 4.x.
- **Negativas:**
  - Nace sin parches OSS (R-01).
  - Obliga a un segundo upgrade si el sistema se mantiene en el tiempo.

## Validación

- `./mvnw help:evaluate -Dexpression=project.parent.version -q -DforceStdout` devuelve `3.5.16`.
- El `pom.xml` no declara versiones de dependencias que gestiona el BOM, salvo las excepciones documentadas en [ADR-007](ADR-007-cve-remediation.md).
