# Análisis de dependencias y build

Ruta base: `legacy/java/jakarta-ee/student-web-app/`

> **No hay `pom.xml` ni `build.gradle`.** Las dependencias salen de 3 lugares: los JARs versionados en `WebContent/WEB-INF/lib/`, las APIs que descarga `setup-docker.sh` a `compile-lib/`, y la librería compartida de Liberty (`mysql-connector/`).

## Dependencias principales

La versión target queda **a confirmar en Fase 2** ([B-06](../blockers.md#b-06)): Spring Boot 3.5 → Spring Framework 6.2; Spring Boot 4.x → Spring Framework 7.0.

| Grupo | Artifact | Versión actual | Ubicación / scope | Versión target | Status |
| --- | --- | --- | --- | --- | --- |
| `org.springframework` | `spring-core` | 5.3.23 | `WEB-INF/lib/spring` | 6.2.x / 7.0.x (vía el BOM de Spring Boot) | Upgrade mayor |
| `org.springframework` | `spring-beans` | 5.3.23 | `WEB-INF/lib/spring` | Ídem | Upgrade mayor |
| `org.springframework` | `spring-context` | 5.3.23 | `WEB-INF/lib/spring` | Ídem | Upgrade mayor |
| `org.springframework` | `spring-aop` | 5.3.23 | `WEB-INF/lib/spring` | Ídem | Upgrade mayor |
| `org.springframework` | `spring-web` | 5.3.23 | `WEB-INF/lib/spring` | Ídem | 🚨 CVEs |
| `org.springframework` | `spring-webmvc` | 5.3.23 | `WEB-INF/lib/spring` | Ídem | Upgrade mayor |
| `org.springframework` | `spring-expression`, `spring-jcl` | **Ausentes** | — | Transitivas con Maven | ⚠ [B-02](../blockers.md#b-02) |
| `com.ibatis` | `ibatis-sqlmap` | 2.3.0 | `WEB-INF/lib/mybatis` | Eliminar (MyBatis 3, JPA o `JdbcClient`) | EOL, reescritura |
| `org.codehaus.jackson` | `jackson-mapper-asl` | 1.9.13 | `WEB-INF/lib/jackson` | Eliminar (Jackson 2 con SB 3 / Jackson 3 con SB 4) | 🚨 CVE, EOL |
| `org.codehaus.jackson` | `jackson-core-asl` | 1.9.13 | `WEB-INF/lib/jackson` | Eliminar | EOL |
| `log4j` | `log4j` | 1.2.17 | `WEB-INF/lib/log4j` | Eliminar (SLF4J + Logback) | 🚨 CVEs, EOL 2015 |
| `javax.servlet` | `javax.servlet-api` | 4.0.1 | `compile-lib/` (provisto por Liberty) | `jakarta.servlet-api` 6.0 (SB 3) / 6.1 (SB 4) | Namespace change |
| `javax.mail` | `javax.mail-api` | 1.6.2 | `compile-lib/` (provisto por Liberty) | `jakarta.mail-api` 2.1 (`spring-boot-starter-mail`) | Namespace change |
| `com.mysql` | `mysql-connector-j` | 8.0.33 | Librería compartida de Liberty | 9.x (gestionado por el BOM) | 🚨 CVE |

### Runtime e infraestructura

| Componente | Versión actual | Fuente | Status |
| --- | --- | --- | --- |
| Open Liberty | 25.0.0.7 kernel-slim, Java 17, OpenJ9 | `Dockerfile` | Se elimina si el target es un JAR de Spring Boot |
| Java (compilación) | 11 | `build.xml` | Target del taller: 21 (Temurin) |
| MySQL server | 8.0 | `docker-compose.yml` | EOL abril 2026 → 8.4 LTS o 9.7 LTS |

## CVEs conocidas

> Se identificaron **por versión**, sin escáner. Antes de cerrar la Fase 1, confirmarlas con OWASP Dependency-Check:
>
> ```bash
> dependency-check --project student-web-app \
>   --scan legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/lib \
>   --out docs/inventory/dependency-check
> ```

### Spring Framework 5.3.23

| CVE | Severidad | Descripción | ¿Explotable aquí? | Corregido en |
| --- | --- | --- | --- | --- |
| CVE-2016-1000027 | Crítica | Deserialización Java en `HttpInvokerServiceExporter` | No: no usa HttpInvoker. Los escáneres la reportan en todo `spring-web` < 6.0 | 6.0 (API eliminada) |
| CVE-2024-22243 | Alta | Open redirect / SSRF al parsear URLs con `UriComponentsBuilder` | Baja: no parsea URLs externas | 5.3.32 |
| CVE-2024-22259 | Alta | Ídem (variante) | Baja | 5.3.33 |
| CVE-2024-22262 | Alta | Ídem (variante) | Baja | 5.3.34 |
| CVE-2023-20860 | Alta | Bypass de seguridad con `**` en `mvcRequestMatcher` | No: no usa Spring Security | 5.3.26 |
| CVE-2024-38816 | Alta | Path traversal en functional web endpoints | No: no usa `RouterFunctions` | Solo versiones comerciales de 5.3.x |
| CVE-2024-38819 | Alta | Path traversal en functional web endpoints (variante) | No | Solo versiones comerciales de 5.3.x |
| CVE-2023-20861 | Media | DoS con expresiones SpEL | No: no evalúa SpEL de usuario | 5.3.26 |
| CVE-2023-20863 | Media | DoS con expresiones SpEL | No | 5.3.27 |
| CVE-2024-38809 | Media | DoS al parsear `ETag` | Baja | 5.3.38 |
| CVE-2024-38808 | Media | DoS con expresiones SpEL | No | 5.3.39 |
| CVE-2024-38820 | Baja | `disallowedFields` de `DataBinder` sensible a mayúsculas | No | Solo versiones comerciales de 5.3.x |

**No afectan a 5.3.23:** CVE-2022-22965 (Spring4Shell, ≤ 5.3.17), CVE-2022-22968, CVE-2022-22970 y CVE-2022-22971.

La rama 5.3 no tiene soporte OSS desde el 31-ago-2024 (último OSS: 5.3.39), así que los CVEs nuevos solo se parchean en versiones comerciales.

### log4j 1.2.17

| CVE | Severidad | Descripción | ¿Explotable aquí? |
| --- | --- | --- | --- |
| CVE-2019-17571 | Crítica | Deserialización en `SocketServer` | No: no se usa |
| CVE-2022-23305 | Crítica | SQL injection en `JDBCAppender` | No: no está configurado |
| CVE-2022-23302 | Alta | Deserialización en `JMSSink` | No |
| CVE-2022-23307 | Alta | Deserialización en Chainsaw | No |
| CVE-2021-4104 | Alta | JNDI en `JMSAppender` | No: no está configurado |

- Solo hay configurados `ConsoleAppender` y `RollingFileAppender`, así que ninguno es explotable directamente. Aun así, log4j 1.x no tiene parches desde 2015 y los escáneres lo bloquean.
- **No** es vulnerable a Log4Shell (CVE-2021-44228), que solo afecta a log4j 2.x.

### Jackson 1.9.13 (codehaus)

| CVE | Severidad | Descripción | ¿Explotable aquí? |
| --- | --- | --- | --- |
| CVE-2019-10172 | Alta | XXE en `jackson-mapper-asl` 1.9.x | Baja: solo serializa con `writeValueAsString` y no deserializa entrada externa |

El proyecto está abandonado desde 2013, así que no habrá parches.

### MySQL Connector/J 8.0.33

| CVE | Severidad | Descripción | Corregido en |
| --- | --- | --- | --- |
| CVE-2023-22102 | Alta | Toma de control de Connector/J (requiere interacción del usuario) | 8.2.0 |

### iBATIS 2.3.0

No tiene CVEs registrados conocidos. El riesgo viene de que está EOL desde 2010 y nunca recibirá parches.

## Build: build.xml

| Elemento | Valor | Observación |
| --- | --- | --- |
| Proyecto / target por defecto | `OpenLibertyApp` / `war` | Genera `dist/OpenLibertyApp.war` |
| `javac` | `source="11" target="11"`, UTF-8, `includeantruntime=false` | Valores hardcodeados; `build.properties` define `java.source`/`java.target` sin usarlos |
| Classpath | `lib/`, `WebContent/WEB-INF/lib/**`, `compile-lib/`, `${user.home}/lib/dev` | `lib/` y `~/lib/dev` no existen: depende del entorno local |
| Recursos | Copia `resources/` a `build/` (classes) | Incluye `log4j.properties`, la config de iBATIS y el XML huérfano |
| `war` | `WEB-INF/lib` aplanado desde 4 subcarpetas | No incluye `spring-jcl` ni `spring-expression` |
| Tests | No hay | Sin JUnit ni target `test` |
| Generación de código | No hay | No hay código `javax.*` generado que regenerar |
| Wrapper | No hay | El lab invoca `./mvnw`: agregar Maven Wrapper en la migración |

### Plugins Maven a considerar en la migración

| Necesidad | Legacy | Equivalente Maven |
| --- | --- | --- |
| Compilación | `<javac source=11>` | `maven-compiler-plugin` con `release` 21 (heredado del parent de Spring Boot) |
| Empaquetado | `<war>` | `spring-boot-maven-plugin` (JAR ejecutable, regla del taller) |
| Recursos | `<copy todir=build>` | `src/main/resources` |
| Escaneo de CVEs | — | `org.owasp:dependency-check-maven` |

## Grafo

Ver [../dependencies.md](../dependencies.md).
