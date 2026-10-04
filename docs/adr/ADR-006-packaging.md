# ADR-006: JAR ejecutable y contenedor multi-stage

| Campo | Valor |
| --- | --- |
| Estado | Aceptado |
| Fecha | 2026-10-04 |
| Relacionado | [B-03](../blockers.md#b-03), [B-04](../blockers.md#b-04), [ADR-001](ADR-001-java-target.md), [ADR-009](ADR-009-web-layer-thymeleaf.md), [ADR-012](ADR-012-config-observability.md), [riesgos](../risks.md) (R-07) |

## Contexto

- El legacy es un WAR desplegado en Open Liberty (puertos 9080/9443). Su imagen es de una sola etapa: copia artefactos construidos fuera de Docker y define contraseñas en `ENV`.
- Reglas del taller: JAR ejecutable, imagen `eclipse-temurin:21-jre-alpine` multi-stage, puerto 8080 y nada de secretos.
- En el Bicep, la Container App Java usa `targetPort: 8080`, 0.5 vCPU / 1 GiB y `minReplicas: 0`.

## Decisión

- Empaquetado `jar` con `spring-boot-maven-plugin` y Tomcat 10.1 embebido, en `server.port=8080`.
- Maven Wrapper (`mvnw`, tipo `only-script`) versionado en `src/student-web-app/`.
- `server.shutdown=graceful`, para respetar el ciclo de vida de ACA.
- `src/student-web-app/Dockerfile` debe cumplir:

| Requisito | Valor |
| --- | --- |
| Etapa de build | `eclipse-temurin:21-jdk-alpine` con `./mvnw -B package -DskipTests` (los tests corren en CI) |
| Caché de dependencias | Copiar `pom.xml`, `mvnw` y `.mvn/` antes que `src/` |
| Etapa de runtime | `eclipse-temurin:21-jre-alpine` |
| Usuario | No-root, UID 1001 |
| Puerto | `EXPOSE 8080` |
| Entrypoint | `java -XX:MaxRAMPercentage=75 -XX:+EnableDynamicAgentLoading -jar /app/app.jar` |
| Secretos | Ninguno en `ENV`, `ARG` ni en las capas |
| `HEALTHCHECK` | No se define: ACA usa probes ([ADR-012](ADR-012-config-observability.md)) |
| `.dockerignore` | `target/`, `.git`, archivos de IDE, `*.md` |

- Del sistema nuevo desaparecen Open Liberty, `server-docker.xml`, `server-docker.env` y el `Dockerfile` y `docker-compose.yml` del legacy. Siguen en `legacy/` como referencia.

## Alternativas consideradas

| Opción | Pros | Contras | Resultado |
| --- | --- | --- | --- |
| WAR ejecutable | Permite seguir con JSP | Rompe la regla del JAR | Descartada |
| WAR en Liberty con Jakarta EE 10 | Menos cambios | No es un JAR de Spring Boot | Descartada |
| Buildpacks (`spring-boot:build-image`) | No requiere Dockerfile | El taller exige Dockerfile e imagen base específica | Descartada |
| **JAR + Dockerfile multi-stage** | Cumple la regla del taller | Hay que reescribir las JSP ([ADR-009](ADR-009-web-layer-thymeleaf.md)) | **Elegida** |

## Consecuencias

- **Positivas:** un solo artefacto y una imagen reproducible, sin pasos manuales previos.
- **Negativas:** el arranque en frío con `minReplicas: 0` puede ser lento (R-07). Más adelante se puede optimizar con CDS o con capas.

## Validación

- `docker build -t student-web-app:local src/student-web-app` funciona sin pasos previos.
- Con `docker run --rm -p 8080:8080 student-web-app:local`, el comando `curl -fsS localhost:8080/actuator/health` devuelve `{"status":"UP"}`.
- `docker inspect` muestra `User` = 1001 y ningún secreto en `Config.Env`.
