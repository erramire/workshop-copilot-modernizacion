# Inventario: dependencias (build Ant, sin `pom.xml`)

> **Sistema analizado:** `legacy/java/jakarta-ee/student-web-app` · **Fase 1 (assessment)** · 2026-10-04
>
> No hay `pom.xml` ni `build.gradle`. El build es **Apache Ant** ([build.xml](../../legacy/java/jakarta-ee/student-web-app/build.xml)) y las dependencias son **JARs vendorizados** en el repositorio o descargados con `curl` por los scripts de setup. Esta página reconstruye el grafo de dependencias como punto de partida para la conversión a Maven.

## De dónde salen las dependencias

| Ubicación | Contenido | Cómo llega | Empaquetado |
| --- | --- | --- | --- |
| `WebContent/WEB-INF/lib/{spring,mybatis,jackson,log4j}/` | 10 JARs de runtime | Binarios versionados en git | Dentro del WAR ([build.xml L52-L63](../../legacy/java/jakarta-ee/student-web-app/build.xml#L52-L63)) |
| `compile-lib/` | `servlet-api.jar`, `javamail-api.jar` | `setup-docker.sh` / `setup-docker.bat` (`curl`) | No entran en el WAR (los aporta Liberty) |
| `mysql-connector/` | Connector/J | `setup-docker.sh` / `setup-docker.bat` (`curl`) | Librería compartida de Liberty ([Dockerfile L14](../../legacy/java/jakarta-ee/student-web-app/Dockerfile#L14)) |
| `${user.home}/lib/dev` | Opcional, vacío por defecto | Manual | Solo classpath de compilación |

## Dependencias principales

| Grupo | Artifact | Versión actual | Scope | Equivalente en el ecosistema Spring Boot 3 | Estado |
| --- | --- | --- | --- | --- | --- |
| org.springframework | spring-core | 5.3.23 | runtime (WAR) | 6.x vía Spring Boot 3 | Upgrade mayor. 5.3 ya no tiene soporte OSS |
| org.springframework | spring-beans | 5.3.23 | runtime | 6.x | Upgrade mayor |
| org.springframework | spring-context | 5.3.23 | runtime | 6.x | Upgrade mayor |
| org.springframework | spring-aop | 5.3.23 | runtime | 6.x | Upgrade mayor |
| org.springframework | spring-web | 5.3.23 | runtime | 6.x (`spring-boot-starter-web`) | Upgrade mayor, con CVEs |
| org.springframework | spring-webmvc | 5.3.23 | runtime | 6.x | Upgrade mayor, con CVEs |
| org.apache.ibatis | ibatis-sqlmap | 2.3.0 | runtime | MyBatis 3, Spring Data JPA o JdbcClient (se decide en Fase 2) | **Reemplazar**: proyecto retirado en 2010 |
| org.codehaus.jackson | jackson-mapper-asl | 1.9.13 | runtime | `com.fasterxml.jackson.core:jackson-databind` 2.x (incluido en Boot) | **Reemplazar**: EOL, con CVE |
| org.codehaus.jackson | jackson-core-asl | 1.9.13 | runtime | Ídem | **Reemplazar** |
| log4j | log4j | 1.2.17 | runtime | SLF4J + Logback (lo que trae Boot por defecto) | **Reemplazar**: EOL desde 2015, CVEs críticas |
| javax.servlet | javax.servlet-api | 4.0.1 | provided | `jakarta.servlet-api` 6.x (Tomcat embebido) | Cambio de namespace |
| javax.mail | javax.mail-api | 1.6.2 | provided | `spring-boot-starter-mail` (Jakarta Mail 2.1 + Angus Mail) | Cambio de namespace |
| com.mysql | mysql-connector-j | 8.0.33 | servidor | `com.mysql:mysql-connector-j` (versión gestionada por Boot) | Upgrade, tiene CVE |

### Dependencias transitivas que faltan (riesgo)

En runtime, `spring-context` y `spring-core` 5.3 necesitan **`spring-expression`** y **`spring-jcl`**, y ninguno de los dos está en `WEB-INF/lib/spring`. Como no se pudo ejecutar el build en este entorno, hay que **comprobar** si la app arranca tal cual o depende de clases que aporta el servidor. Al pasar a Maven el problema desaparece, porque las dependencias transitivas se resuelven solas.

## CVEs conocidas

> **Fuentes:** avisos públicos de spring.io/security, NVD y Oracle CPU. La lista **no es exhaustiva**, porque en este entorno no hay herramienta de escaneo. En cuanto exista el `pom.xml`, hay que ejecutar **OWASP Dependency-Check** (o activar Dependabot).
> La columna "¿Explotable hoy?" valora el uso real que hace el código, no solo la versión.

### Spring Framework 5.3.23

El soporte OSS de 5.3.x terminó con la 5.3.39 (agosto de 2024). Las correcciones posteriores (5.3.41 en adelante) solo se publican con soporte comercial. La versión 5.3.23 **no** está afectada por Spring4Shell (CVE-2022-22965, corregida en 5.3.18).

| CVE | Componente | Severidad | Corregida en | ¿Explotable hoy? |
| --- | --- | --- | --- | --- |
| CVE-2016-1000027 | spring-web (`HttpInvokerServiceExporter`, deserialización) | Crítica (NVD 9.8, disputada) | 6.0.0 (la API se eliminó) | No, no se usa HttpInvoker. Los escáneres la reportan de todos modos |
| CVE-2023-20860 | spring-webmvc (patrón `**` con `mvcRequestMatcher`) | Crítica (según spring.io) | 5.3.26 | No, no hay Spring Security |
| CVE-2023-20861 | spring-expression (DoS con SpEL) ¹ | Media | 5.3.26 | No, no se evalúa SpEL que venga del usuario |
| CVE-2023-20863 | spring-expression (DoS con SpEL) ¹ | Media | 5.3.27 | No |
| CVE-2024-22243 | spring-web (`UriComponentsBuilder`, open redirect/SSRF) | Alta | 5.3.32 | No, no se usa `UriComponentsBuilder` |
| CVE-2024-22259 | spring-web (ídem) | Alta | 5.3.33 | No |
| CVE-2024-22262 | spring-web (ídem) | Alta | 5.3.34 | No |
| CVE-2024-38809 | spring-web (ETag en `If-Match`/`If-None-Match`, DoS) | Media | 5.3.38 | No, no hay `ShallowEtagHeaderFilter` ni `checkNotModified` |
| CVE-2024-38808 | spring-expression (DoS con SpEL) ¹ | Media | 5.3.39 | No |
| CVE-2024-38820 | spring-context (`DataBinder` `disallowedFields`) | Baja | 5.3.41 (solo comercial) | No |
| CVE-2024-38828 | spring-webmvc (`@RequestBody byte[]`, DoS) | Media | 5.3.42 (solo comercial) | No |
| CVE-2025-22233 | spring-context (`DataBinder` `disallowedFields`) | Baja | 5.3.43 (solo comercial) | No |
| CVE-2025-41242 | spring-webmvc (path traversal en contenedores no conformes) | Media | 5.3.44 (solo comercial) | No, los estáticos los sirve el contenedor y no el resource handling de Spring |

¹ Afecta a `spring-expression` 5.3.23, que hoy ni siquiera está en `WEB-INF/lib` (ver dependencias transitivas que faltan).

### log4j 1.2.17 (EOL desde agosto de 2015)

| CVE | Clase afectada | Severidad | Corrección | ¿Explotable hoy? |
| --- | --- | --- | --- | --- |
| CVE-2019-17571 | `SocketServer` (deserialización) | Crítica 9.8 | No hay en 1.x | No, no se usa `SocketServer` |
| CVE-2022-23305 | `JDBCAppender` (SQL injection) | Crítica 9.8 | No hay en 1.x | No, no está configurado |
| CVE-2022-23302 | `JMSSink` (JNDI) | Alta 8.8 | No hay en 1.x | No |
| CVE-2022-23307 | Chainsaw (deserialización) | Alta 8.8 | No hay en 1.x | No |
| CVE-2021-4104 | `JMSAppender` (JNDI) | Alta 7.5 | No hay en 1.x | No, solo hay `ConsoleAppender` y `RollingFileAppender` |

- Log4Shell (CVE-2021-44228) **no** le afecta: es de log4j 2.x.
- El riesgo real es doble: basta un cambio en `log4j.properties` para activar un appender vulnerable, y los escáneres lo marcan como crítico, lo que bloquea cualquier revisión de compliance.

### Jackson 1.9.13 (`org.codehaus`, EOL)

| CVE | Severidad | ¿Explotable hoy? |
| --- | --- | --- |
| CVE-2019-10172 (XXE en `jackson-mapper-asl`) | Alta 7.5 | No, el código solo serializa (`writeValueAsString`) |

Algunos escáneres le asocian también la familia de CVEs de deserialización polimórfica de Jackson (p. ej. CVE-2017-7525). Esas requieren *default typing*, que aquí no se usa. La rama 1.x no tiene versiones corregidas.

### MySQL Connector/J 8.0.33

| CVE | Severidad | Corregida en | ¿Explotable hoy? |
| --- | --- | --- | --- |
| CVE-2023-22102 | Alta 8.3 | 8.2.0 | Difícil de explotar porque requiere interacción. Conviene actualizar igualmente |

### iBATIS 2.3.0

No se conocen CVEs publicadas. Pero el proyecto está retirado desde 2010 (Apache Attic), así que **ningún fallo futuro tendrá parche**.

## Build con Ant

| Aspecto | Valor | Evidencia |
| --- | --- | --- |
| Herramienta | Apache Ant (el README indica que se probó con 1.10.14) | [build.xml](../../legacy/java/jakarta-ee/student-web-app/build.xml) |
| Targets | `clean`, `compile` y `war` (por defecto) | L20-L65 |
| `javac` source/target | **11**, fijado en el propio `build.xml`. `build.properties` define `java.source/target`, pero `build.xml` no los usa | [L27](../../legacy/java/jakarta-ee/student-web-app/build.xml#L27) |
| Packaging | WAR (`dist/OpenLibertyApp.war`) | [L43-L65](../../legacy/java/jakarta-ee/student-web-app/build.xml#L43-L65) |
| Generación de código (XMLBeans, JAXB, WSDL) | No | — |
| Tests (task `junit`) | No | — |
| Gestión de dependencias (Ivy o tasks de Maven) | No | — |
| Verificación de integridad de los JARs | No: los JARs binarios están en git y `curl` se ejecuta sin checksum ni `-f` | [setup-docker.sh](../../legacy/java/jakarta-ee/student-web-app/setup-docker.sh) |

Problema en los scripts de setup:
- `setup-docker.bat` descarga Connector/J desde una URL incorrecta: `mysql/mysql-connector-java/8.0.33/mysql-connector-j-8.0.33.jar` ([L36](../../legacy/java/jakarta-ee/student-web-app/setup-docker.bat#L36)). Lo más probable es que reciba un 404 y que `curl -L -o` sin `-f` guarde la página de error como si fuera el `.jar`. `setup-docker.sh` usa la URL correcta (`com/mysql/mysql-connector-j/...`). **Esto afecta a quien haga el taller en Windows.**

## Plataforma de ejecución actual

| Aspecto | Valor |
| --- | --- |
| Imagen | `open-liberty:25.0.0.7-kernel-slim-java17-openj9`, single-stage (necesita el WAR ya compilado en el host) |
| JVM de runtime | Java 17 (OpenJ9); el código se compila para Java 11 |
| Puertos | 9080 y 9443 (el target del workshop es 8080) |
| Features de Liberty en uso | `servlet-4.0`, `jsp-2.3`, `jdbc-4.3`, `jndi-1.0`, `javaMail-1.6`, `webProfile-8.0`, `transportSecurity-1.0` (HTTPS en 9443) |
| Features de Liberty sin uso aparente | `jaxws-2.2`, `springBoot-2.0`, `appSecurity-3.0`, `localConnector-1.0` |
| Base de datos local | `mysql:8.0` (MySQL 8.0 llegó a fin de vida en abril de 2026) |
