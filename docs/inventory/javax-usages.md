# Inventario: uso de `javax.*` (cambio de namespace a `jakarta.*`)

> **Sistema analizado:** `legacy/java/jakarta-ee/student-web-app` · **Fase 1 (assessment)** · 2026-10-04
> Este es el mapa de entrada para el paso de OpenRewrite en Fase 3.

## Resumen

| Métrica | Valor |
| --- | --- |
| Archivos Java totales | 9 |
| Archivos Java con `javax.*` afectado | **4 (44%)** |
| Líneas `import` que hay que cambiar | **24** (`javax.servlet` ×19, `javax.mail` ×5) |
| `javax.*` del JDK que **no** cambian | `javax.naming` ×2 (JNDI) y `javax.sql` (en `web.xml`) |
| Descriptores XML afectados | `web.xml` (namespace y `res-type javax.mail.Session`) |
| JSP afectadas | Ninguna importa `javax` explícitamente; sus objetos implícitos cambian junto con el contenedor |
| `javax.persistence`, `javax.validation`, `javax.annotation`, `javax.transaction`, `javax.ejb`, `javax.jms`, `javax.xml.bind`, `javax.ws` | 0 |

## Detalle por archivo

Rutas relativas a `src/org/sample/azure/student/coreft/`.

| Archivo | Paquetes | Imports | Líneas |
| --- | --- | --- | --- |
| [AddStudentServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L12-L20) | `javax.servlet` (4), `javax.mail` (5) | 9 | L12-L20 |
| [filter/CommonHttpServletFilter.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/filter/CommonHttpServletFilter.java#L3-L9) | `javax.servlet` (7) | 7 | L3-L9 |
| [IndexServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/IndexServlet.java#L7-L10) | `javax.servlet` (4) | 4 | L7-L10 |
| [StudentProfileListServlet.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfileListServlet.java#L11-L14) | `javax.servlet` (4) | 4 | L11-L14 |

Imports distintos afectados:

| Import actual | Import Jakarta |
| --- | --- |
| `javax.servlet.ServletException` | `jakarta.servlet.ServletException` |
| `javax.servlet.http.HttpServlet` | `jakarta.servlet.http.HttpServlet` |
| `javax.servlet.http.HttpServletRequest` | `jakarta.servlet.http.HttpServletRequest` |
| `javax.servlet.http.HttpServletResponse` | `jakarta.servlet.http.HttpServletResponse` |
| `javax.servlet.Filter`, `FilterChain`, `FilterConfig`, `ServletRequest`, `ServletResponse` | `jakarta.servlet.*` |
| `javax.mail.Message`, `Session`, `Transport` | `jakarta.mail.*` |
| `javax.mail.internet.InternetAddress`, `MimeMessage` | `jakarta.mail.internet.*` |

No cambian (forman parte del JDK): `javax.naming.Context` y `javax.naming.InitialContext` ([AddStudentServlet.java L10-L11](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L10-L11)).

Archivos sin `javax.*`: `StudentController`, `AddStudentController`, `StudentService`, `MyBatisUtil` y `StudentProfile`.

## Descriptores y configuración

| Archivo | Elemento | Cambio necesario |
| --- | --- | --- |
| [web.xml L2-L5](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml#L2-L5) | `xmlns.jcp.org/xml/ns/javaee` con `version="4.0"` | `https://jakarta.ee/xml/ns/jakartaee` con `version="6.0"`, salvo que `web.xml` desaparezca al pasar a Spring Boot en JAR |
| [web.xml L74](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml#L74) | `<res-type>javax.mail.Session</res-type>` | `jakarta.mail.Session` |
| [web.xml L67](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml#L67) | `<res-type>javax.sql.DataSource</res-type>` | Ninguno (es del JDK) |
| [server-docker.xml L3-L13](../../legacy/java/jakarta-ee/student-web-app/liberty_config/server-docker.xml#L3-L13) | `servlet-4.0`, `jsp-2.3`, `javaMail-1.6`, `webProfile-8.0`, `jaxws-2.2`, `appSecurity-3.0`, `springBoot-2.0` | Solo si se mantiene Liberty: `servlet-6.0`, `pages-3.1`, `mail-2.1`, `webProfile-10.0`, `xmlWS-4.0`, `appSecurity-5.0`, `springBoot-3.0` |

## Librerías atadas a `javax.*`

| Librería | Dependencia de `javax` | Efecto |
| --- | --- | --- |
| Spring 5.3.23 (spring-web, spring-webmvc) | `javax.servlet` | Obliga a subir a Spring 6.x (jakarta) |
| javax.servlet-api 4.0.1 / javax.mail-api 1.6.2 | Son la propia API `javax` | Sustituir por `jakarta.servlet-api` y Jakarta Mail 2.1 |
| iBATIS 2.3.0 | Solo JDBC y JNDI del JDK (JTA es opcional y no se usa) | No bloquea el cambio de namespace, pero tiene sus propios bloqueos (ver [persistence.md](persistence.md)) |
| log4j 1.2.17 | `javax.jms`/`javax.mail`, solo en appenders que no están configurados | No bloquea; se sustituye de todas formas |
| Jackson 1.9.13 | Ninguna relevante | Se sustituye de todas formas |

## Herramienta sugerida

La receta de OpenRewrite `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta` cubre los 24 imports (`javax.servlet` y `javax.mail`). Hay que comprobar a mano si también actualiza `web.xml` y los features de Liberty, en caso de que sigan existiendo después de las decisiones de Fase 2.
