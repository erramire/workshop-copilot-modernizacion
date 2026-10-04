# Inventario: usos de `javax.*` (cambio de namespace a `jakarta.*`)

> Sistema: `legacy/java/jakarta-ee/student-web-app/`. Las rutas son relativas a esa carpeta.

## Resumen

| Métrica | Valor |
| --- | --- |
| Archivos Java con imports `javax.*` | **4 de 9** |
| Imports `javax.*` en total | 26 |
| Imports que **cambian** a `jakarta.*` | **24**: `javax.servlet` ×19 y `javax.mail` ×5 |
| Imports que **no cambian** (son Java SE) | 2: `javax.naming` |
| Descriptores XML afectados | 1: `web.xml` (namespace y `res-type javax.mail.Session`) |
| JSP con imports `javax.*` | 0 |
| Configuración de servidor afectada | `server-docker.xml` (features Java EE 8), solo si el target sigue en Liberty |
| Código generado (JAXB, XMLBeans) | Ninguno |
| `javax.persistence`, `javax.validation`, `javax.annotation`, `javax.ejb`, `javax.jms`, `javax.xml.bind`, `javax.ws.rs`, `javax.inject` | 0 |

**Magnitud: baja.** El cambio es mecánico y toca 4 archivos Java y 1 descriptor. El Lab 02 anticipa "30+ archivos", pero eso no aplica a este sistema.

## Detalle por archivo

| Archivo | Líneas | Imports que cambian | Imports que no cambian | Nota |
| --- | --- | --- | --- | --- |
| `src/org/sample/azure/student/coreft/AddStudentServlet.java` | 10-20 | 9: `javax.servlet.ServletException`, `javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}`, `javax.mail.{Message, Session, Transport}`, `javax.mail.internet.{InternetAddress, MimeMessage}` | 2: `javax.naming.{Context, InitialContext}` | También hace lookup JNDI de `java:comp/env/mail/StudentMailSession` (línea 99) |
| `src/org/sample/azure/student/coreft/filter/CommonHttpServletFilter.java` | 3-9 | 7: `javax.servlet.{Filter, FilterChain, FilterConfig, ServletException, ServletRequest, ServletResponse}`, `javax.servlet.http.HttpServletRequest` | — | Clase no registrada (código muerto) |
| `src/org/sample/azure/student/coreft/IndexServlet.java` | 7-10 | 4: `javax.servlet.ServletException`, `javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}` | — | — |
| `src/org/sample/azure/student/coreft/StudentProfileListServlet.java` | 11-14 | 4: `javax.servlet.ServletException`, `javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}` | — | — |

Archivos Java **sin** `javax.*`: `StudentController`, `AddStudentController`, `StudentService`, `StudentProfile` y `MyBatisUtil`.

## Descriptores y configuración

| Archivo | Línea | Valor actual | Cambio requerido |
| --- | --- | --- | --- |
| `WebContent/WEB-INF/web.xml` | 2-5 | `xmlns="http://xmlns.jcp.org/xml/ns/javaee"`, `web-app_4_0.xsd`, `version="4.0"` | `https://jakarta.ee/xml/ns/jakartaee`, `web-app_6_0.xsd`, `version="6.0"`. Solo si se conserva `web.xml` (en un JAR de Spring Boot desaparece) |
| `WebContent/WEB-INF/web.xml` | 74 | `<res-type>javax.mail.Session</res-type>` | `jakarta.mail.Session` |
| `WebContent/WEB-INF/web.xml` | 67 | `<res-type>javax.sql.DataSource</res-type>` | Sin cambio (Java SE) |
| `liberty_config/server-docker.xml` | 3-13 | `servlet-4.0`, `jsp-2.3`, `javaMail-1.6`, `webProfile-8.0`, `jaxws-2.2` | Equivalentes de Jakarta EE 10 (`servlet-6.0`, `pages-3.1`, `mail-2.1`, `webProfile-10.0`). Solo si el target sigue en Liberty |

## Librerías de terceros y el namespace

| Librería | ¿Depende de `javax.servlet` / `javax.mail`? | Implicación |
| --- | --- | --- |
| Spring Framework 5.3.23 | Sí (compilado contra `javax.servlet`) | El cambio de namespace **obliga** a subir a Spring 6.x en el mismo paso |
| iBATIS 2.3.0 | No (con `transactionManager type="JDBC"`) | No bloquea el namespace, pero se reemplaza por EOL |
| Jackson 1.9.13 | No | Igual que iBATIS |
| Log4j 1.2.17 | Solo `javax.jms` y `javax.mail` en appenders que no se usan | Igual que iBATIS |

## Herramienta sugerida

OpenRewrite `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta`, el recipe `javax-to-jakarta` que mencionan las instrucciones del workshop.

> **Restricción de secuencia:** OpenRewrite se ejecuta con `rewrite-maven-plugin` o `rewrite-gradle-plugin`. Este proyecto es **Ant** y no tiene `pom.xml`, así que hay que convertir Ant a Maven (o crear un `pom.xml` mínimo) **antes** de aplicar el recipe. Las instrucciones del workshop indican "OpenRewrite como primer paso"; Fase 2 debe resolver este orden de forma explícita.

## Verificación posterior (Fase 3)

Una vez migrado, esta búsqueda no debería devolver resultados en el código nuevo:

```bash
grep -rnE "^import javax\.(servlet|mail)\." <src-migrado>
```
