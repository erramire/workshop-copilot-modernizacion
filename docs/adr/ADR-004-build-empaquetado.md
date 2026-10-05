# ADR-004: Build, empaquetado y dependencias

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisiones por defecto del planning, aceptadas en el chat)

## Contexto
- El legacy se construye con Ant a partir de JARs vendorizados sin checksums. Genera un WAR para Open Liberty, y su Dockerfile de una sola etapa necesita el WAR ya compilado fuera (PLT-03, PLT-06).
- El classpath legacy está incompleto: faltan `spring-expression` y `spring-jcl` (PLT-04).
- Reglas del taller: Maven, JAR ejecutable, imagen multi-stage sobre `eclipse-temurin:21-jre-alpine` y puerto 8080.
- El paso 8 del lab 02 construye con `mvnw package -DskipTests` sobre `eclipse-temurin:21-jdk-alpine` y ejecuta con un usuario no root de uid 1001.

## Opciones consideradas
1. **Maven, JAR ejecutable y Tomcat embebido.**
   - A favor: es el estándar de Spring Boot y lo que exige el taller.
2. **Maven y WAR desplegado en un servidor externo** (Liberty o Tomcat).
   - En contra: incumple la regla del JAR y arrastra la configuración del servidor.
3. **Gradle.**
   - En contra: no aporta nada aquí y el taller y los agentes asumen Maven.

## Decisión
Opción 1.

### Proyecto Maven

| Elemento | Valor |
| --- | --- |
| Coordenadas | `org.sample.azure:student-web-app:1.0.0` |
| Parent | `org.springframework.boot:spring-boot-starter-parent:3.5.16` |
| `java.version` | 21 |
| Packaging | `jar`, con `finalName` = `student-web-app` |
| Maven Wrapper | Incluido (`mvnw` y `mvnw.cmd`, con Maven 3.9.x) |

### Dependencias finales
Las versiones las fija el BOM de Spring Boot, salvo que se indique otra cosa.

| Dependencia | Scope | Uso |
| --- | --- | --- |
| `spring-boot-starter-web` | compile | Spring MVC y Tomcat embebido |
| `spring-boot-starter-thymeleaf` | compile | Vistas ([ADR-008](ADR-008-frontend.md)) |
| `spring-boot-starter-validation` | compile | Bean Validation ([ADR-009](ADR-009-seguridad.md)) |
| `spring-boot-starter-data-jpa` | compile | Persistencia ([ADR-006](ADR-006-persistencia.md)) |
| `spring-boot-starter-security` | compile | CSRF y cabeceras de seguridad ([ADR-009](ADR-009-seguridad.md)) |
| `spring-boot-starter-mail` | compile | Email de bienvenida ([ADR-011](ADR-011-notificacion-email.md)) |
| `spring-boot-starter-actuator` | compile | `/health` ([ADR-010](ADR-010-configuracion-observabilidad.md)) |
| `org.flywaydb:flyway-core` y `org.flywaydb:flyway-mysql` | compile | Migraciones de esquema ([ADR-005](ADR-005-base-de-datos.md)) |
| `com.h2database:h2` | runtime | Base de datos por defecto |
| `com.mysql:mysql-connector-j` | runtime | MySQL opcional |
| `spring-boot-starter-test` y `spring-security-test` | test | Pruebas ([ADR-012](ADR-012-pruebas.md)) |

### Dependencias temporales

| Dependencia | Scope | Se elimina en |
| --- | --- | --- |
| `javax.servlet:javax.servlet-api:4.0.1` | provided | Paso 1, después de OpenRewrite |
| `javax.mail:javax.mail-api:1.6.2` | provided | Paso 1 |
| `log4j:log4j:1.2.17` | provided | Paso 1 |
| `org.apache.ibatis:ibatis-sqlmap:2.3.0` | compile | Paso 4 |

### Plugins
- `spring-boot-maven-plugin`, para generar el JAR ejecutable.
- `maven-dependency-plugin`, que copia `com.microsoft.azure:applicationinsights-agent:3.7.9` a `target/agent/applicationinsights-agent.jar` ([ADR-010](ADR-010-configuracion-observabilidad.md)).
- `org.owasp:dependency-check-maven`, dentro de un perfil `security-scan` que no forma parte del build normal. Falla con CVSS ≥ 7 y necesita la variable `NVD_API_KEY`.

### Imagen de contenedor (`src/student-web-app/Dockerfile`)
- **Etapa de build:** `eclipse-temurin:21-jdk-alpine`, con `./mvnw -B package -DskipTests`. Los tests se ejecutan aparte, en el paso de verificación.
- **Etapa de runtime:** `eclipse-temurin:21-jre-alpine`, usuario no root con uid 1001 y `EXPOSE 8080`. Copia el JAR y el agente.
- El `ENTRYPOINT` solo adjunta el agente de Application Insights si existe la variable `APPLICATIONINSIGHTS_CONNECTION_STRING` ([ADR-010](ADR-010-configuracion-observabilidad.md)).
- Un `.dockerignore` excluye `target/`, `.git` y los archivos del IDE.

### Gobierno de dependencias
- No se declaran versiones explícitas, salvo las de las tablas de este ADR.
- Dependabot: `.github/dependabot.yml` con el ecosistema `maven` en `/src/student-web-app` y frecuencia semanal.

### Lo que no se traslada
`build.xml`, `build.properties`, `setup-docker.*`, `WEB-INF/lib/**`, `liberty_config/` y `docker-compose.yml`.

## Consecuencias
- **Positivas:**
  - El build es reproducible.
  - El grafo de dependencias se resuelve solo, lo que corrige PLT-04.
  - Desaparecen las CVEs del legacy al eliminar log4j 1, Jackson 1, iBATIS y Spring 5.3.
- **Negativas:** el escaneo de OWASP necesita una clave de NVD y tarda, así que no forma parte del build por defecto.
- **Riesgos a monitorear:** R-12 y R-14 en [risks.md](../risks.md).

## Referencias
- [inventory/dependencies-pom.md](../inventory/dependencies-pom.md) y [blockers.md](../blockers.md) (PLT-03, PLT-04, PLT-06, SEC-08)
