# Inventario: dependencias (build Ant, sin pom.xml)

> Sistema: `legacy/java/jakarta-ee/student-web-app/`. Las rutas son relativas a esa carpeta.
> **No hay `pom.xml` ni `build.gradle`.** El build es Ant (`build.xml`) con jars copiados a mano al repositorio. Este documento cumple el rol de `dependencies-pom.md` y analiza los jars reales que se empaquetan.

## Build actual

| Aspecto | Valor | Evidencia |
| --- | --- | --- |
| Herramienta | Apache Ant (el README dice que se probó con 1.10.14) | `build.xml` |
| Target por defecto | `war` (depende de `compile`) | `build.xml:2` |
| Java source/target | **11** | `build.xml:27`, `build.properties` |
| Artefacto | `dist/OpenLibertyApp.war` | `build.xml:13` |
| Classpath de compilación | `lib/`, `WebContent/WEB-INF/lib/**`, `compile-lib/` (APIs) y `~/lib/dev` (opcional) | `build.xml:30-35` |
| Jars empaquetados en el WAR | `WEB-INF/lib/{log4j,jackson,mybatis,spring}/*.jar`, aplanados | `build.xml:52-63` |
| Tests | No hay target de test ni tests | — |
| Gestión de dependencias transitivas | **Ninguna** (manual) | — |

## Dependencias de runtime (empaquetadas en el WAR)

La columna "Versión de referencia" usa Spring Boot 3.5, que gestiona Spring Framework 6.2.x. Es la misma línea de Spring que pide el Lab 02. La versión final la decide Fase 2.

| Grupo | Artifact | Versión actual | Versión de referencia | Estado y efecto del cambio |
| --- | --- | --- | --- | --- |
| org.springframework | spring-core | 5.3.23 | 6.2.x | Upgrade mayor. Exige Java 17+ y Jakarta EE 9+ |
| org.springframework | spring-beans | 5.3.23 | 6.2.x | Igual que spring-core |
| org.springframework | spring-context | 5.3.23 | 6.2.x | Igual que spring-core |
| org.springframework | spring-aop | 5.3.23 | 6.2.x | Igual que spring-core |
| org.springframework | spring-web | 5.3.23 | 6.2.x | Igual que spring-core. Además elimina HttpInvoker (CVE-2016-1000027) |
| org.springframework | spring-webmvc | 5.3.23 | 6.2.x | Igual que spring-core. Requiere Servlet 6.0 (`jakarta.servlet`) |
| org.springframework | spring-expression | **falta** | 6.2.x | Requerida por `spring-context` 5.3. Ver riesgo más abajo |
| org.springframework | spring-jcl | **falta** | 6.2.x | Requerida por `spring-core` 5.3. Ver riesgo más abajo |
| org.codehaus.jackson | jackson-core-asl | 1.9.13 | Eliminar y usar `com.fasterxml.jackson.core:jackson-databind` 2.x | 🚨 EOL. Cambia el paquete `org.codehaus.jackson` por `com.fasterxml.jackson` (1 archivo: `StudentProfileListServlet.java:6`) |
| org.codehaus.jackson | jackson-mapper-asl | 1.9.13 | Eliminar (igual que la anterior) | 🚨 CVE-2019-10172 |
| log4j | log4j | 1.2.17 | Eliminar y usar SLF4J + Logback (default de Spring Boot) | 🚨 EOL desde 2015, 5 CVEs. Cambia la API de logger en 6 clases |
| org.apache.ibatis | ibatis-sqlmap | 2.3.0 | Eliminar. El reemplazo lo decide Fase 2 (MyBatis 3, Spring JDBC o JPA) | ⚠️ Proyecto retirado por Apache en 2010. Cambian la API y la sintaxis de mapeo (ver [persistence.md](persistence.md)) |

Clases afectadas por el cambio de logger (`org.apache.log4j.Logger`): `StudentController`, `AddStudentController`, `StudentService`, `IndexServlet`, `AddStudentServlet` y `StudentProfileListServlet`.

## Dependencias provistas por el servidor o solo de compilación

| Grupo | Artifact | Versión | Origen | Estado |
| --- | --- | --- | --- | --- |
| javax.servlet | javax.servlet-api | 4.0.1 | `compile-lib/` (lo descarga `setup-docker.sh`). En runtime, Liberty `servlet-4.0` | Pasa a `jakarta.servlet-api` 6.x (namespace) |
| javax.mail | javax.mail-api | 1.6.2 | `compile-lib/`. En runtime, Liberty `javaMail-1.6` | Pasa a `jakarta.mail` 2.x (Angus Mail en Spring Boot 3) |
| com.mysql | mysql-connector-j | 8.0.33 | Librería compartida de Liberty (`mysql-connector/`, montada en el contenedor) | 🚨 CVE-2023-22102. Corregido en ≥ 8.2.0 |
| — | JSP 2.3 | — | Liberty `jsp-2.3` | Pasa a Jakarta Pages 3.x si se conservan las JSP |

## Runtime y plataforma

| Componente | Versión | Evidencia | Nota |
| --- | --- | --- | --- |
| Open Liberty | 25.0.0.7 (kernel-slim, OpenJ9) | `Dockerfile:1` | Runtime actual. Lo que está desactualizado es el stack de la app, no el servidor |
| JVM en runtime | Java 17 (OpenJ9) | `Dockerfile:1` | Se compila a 11 y se ejecuta en 17 |
| Features de Liberty | `servlet-4.0`, `jsp-2.3`, `jdbc-4.3`, `jndi-1.0`, `javaMail-1.6`, `webProfile-8.0`, `appSecurity-3.0`, `transportSecurity-1.0`, `jaxws-2.2`, `springBoot-2.0`, `localConnector-1.0` | `server-docker.xml:3-13` | Java EE 8. `jaxws-2.2` y `springBoot-2.0` no se usan |
| MySQL | 8.0 | `docker-compose.yml:3` | — |

## Riesgo: faltan jars transitivos de Spring

En Spring 5.3, `spring-context` depende de `spring-expression` y `spring-core` depende de `spring-jcl`. Ninguno de los dos está en `WEB-INF/lib/spring/`, y `build.xml` solo empaqueta esas 4 carpetas. Si el servidor no los provee, al arrancar los contextos Spring cabe esperar un `NoClassDefFoundError` (`org/apache/commons/logging/LogFactory` o `org/springframework/expression/...`).

**En este assessment no se pudo validar en runtime.** Es el síntoma típico de gestionar dependencias a mano, y un argumento directo para pasar a Maven.

## Plugins de build (equivalencias)

| Plugin Maven típico | Equivalente actual en Ant | Nota |
| --- | --- | --- |
| `maven-compiler-plugin` | `<javac source="11" target="11">` | — |
| `maven-war-plugin` | Tarea `<war>` | Si el target es un JAR ejecutable, desaparece |
| Generación de código (JAXB, XMLBeans) | No hay | No hay código generado con `javax.*` que regenerar |
| Plugins de test (Surefire / Failsafe) | No hay | No hay tests |

## Scripts de setup

- `setup-docker.sh` descarga `javax.servlet-api` 4.0.1, `javax.mail-api` 1.6.2 y `mysql-connector-j` 8.0.33 con `curl -L -o`, **sin `-f`**. Ante un 404, el script guarda un HTML con extensión `.jar` y reporta éxito.
- `setup-docker.bat` usa otra URL para Connector/J (`mysql/mysql-connector-java/8.0.33/mysql-connector-j-8.0.33.jar`), distinta de la del `.sh`. Es probable que dé 404 en Windows.
- El `Dockerfile` copia un WAR ya construido (`dist/OpenLibertyApp.war`): no es multi-stage y depende de ejecutar Ant en el host.

## CVEs

Resumen. El detalle está en [cve-report.md](cve-report.md).

| Dependencia | CVEs identificadas | Severidad máxima | ¿Explotable con el código actual? |
| --- | --- | --- | --- |
| log4j 1.2.17 | 5 | Crítica (9.8) | No directamente: solo se usan `ConsoleAppender` y `RollingFileAppender`. Pero está EOL y no hay parche |
| Spring Framework 5.3.23 | 14 | Crítica (CVE-2016-1000027, CVE-2023-20860) | No: las condiciones de explotación no están presentes |
| mysql-connector-j 8.0.33 | 1 | Alta (8.3) | La versión está afectada; la explotabilidad no se puede determinar |
| jackson-mapper-asl 1.9.13 | 1 | Alta (7.5) | Baja: solo se serializa |
| ibatis-sqlmap 2.3.0 | 0 identificadas | — | Proyecto retirado: no habrá parches futuros |

**No se ejecutó un escaneo automatizado** (OWASP Dependency-Check) porque no hay `pom.xml`. Cuando exista, hay que ejecutarlo para confirmar este análisis manual.
