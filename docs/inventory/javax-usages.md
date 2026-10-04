# Uso de `javax.*`: mapa del namespace change

Ruta base: `legacy/java/jakarta-ee/student-web-app/`

## Resumen

| Métrica | Valor |
| --- | --- |
| Archivos Java con imports afectados | **4 de 9** (44%) |
| Imports a cambiar | **24** (`javax.servlet` ×19, `javax.mail` ×5) |
| Imports `javax.*` que **no** cambian | 2 (`javax.naming`, Java SE) |
| Archivos no Java afectados | `web.xml`, `server-docker.xml` (features), `setup-docker.sh` y `setup-docker.bat` (APIs de compilación) |
| JSPs afectadas | 0 imports `javax.*`; se recompilan en un contenedor Jakarta sin cambios de código |
| Código generado con `javax.*` | No hay |

> Spring 5.3 solo funciona con `javax.*` y Spring 6+ solo con `jakarta.*`. El namespace change y el upgrade de Spring deben hacerse **en el mismo paso** ([B-07](../blockers.md#b-07)).

## Por archivo Java

| Archivo | Paquetes afectados | Imports afectados | Sin cambio |
| --- | --- | --- | --- |
| `src/org/sample/azure/student/coreft/AddStudentServlet.java` | `javax.servlet` (4), `javax.mail` (5) | 9 | `javax.naming` (2) |
| `src/org/sample/azure/student/coreft/filter/CommonHttpServletFilter.java` | `javax.servlet` (7) | 7 | — |
| `src/org/sample/azure/student/coreft/IndexServlet.java` | `javax.servlet` (4) | 4 | — |
| `src/org/sample/azure/student/coreft/StudentProfileListServlet.java` | `javax.servlet` (4) | 4 | — |
| **Total** | | **24** | 2 |

Los 5 archivos restantes (`StudentProfile`, `StudentController`, `AddStudentController`, `StudentService`, `MyBatisUtil`) no importan `javax.*`.

### Detalle de imports

| Import legacy | Import destino | Archivos |
| --- | --- | --- |
| `javax.servlet.ServletException` | `jakarta.servlet.ServletException` | AddStudent, Index, StudentProfileList, CommonHttpServletFilter |
| `javax.servlet.http.HttpServlet` | `jakarta.servlet.http.HttpServlet` | AddStudent, Index, StudentProfileList |
| `javax.servlet.http.HttpServletRequest` | `jakarta.servlet.http.HttpServletRequest` | AddStudent, Index, StudentProfileList, CommonHttpServletFilter |
| `javax.servlet.http.HttpServletResponse` | `jakarta.servlet.http.HttpServletResponse` | AddStudent, Index, StudentProfileList |
| `javax.servlet.Filter`, `FilterChain`, `FilterConfig`, `ServletRequest`, `ServletResponse` | `jakarta.servlet.*` | CommonHttpServletFilter |
| `javax.mail.Message`, `Session`, `Transport` | `jakarta.mail.*` | AddStudent |
| `javax.mail.internet.InternetAddress`, `MimeMessage` | `jakarta.mail.internet.*` | AddStudent |
| `javax.naming.Context`, `InitialContext` | **Sin cambio** (Java SE) | AddStudent |

## Archivos no Java

| Archivo | Línea | Contenido actual | Cambio requerido |
| --- | --- | --- | --- |
| `WebContent/WEB-INF/web.xml` | 2–5 | `xmlns="http://xmlns.jcp.org/xml/ns/javaee"`, `web-app_4_0.xsd`, `version="4.0"` | `https://jakarta.ee/xml/ns/jakartaee`, `web-app_6_0.xsd`, `version="6.0"`; con Spring Boot, `web.xml` desaparece |
| `WebContent/WEB-INF/web.xml` | 67 | `<res-type>javax.sql.DataSource</res-type>` | **Sin cambio** (Java SE) |
| `WebContent/WEB-INF/web.xml` | 74 | `<res-type>javax.mail.Session</res-type>` | `jakarta.mail.Session` |
| `liberty_config/server-docker.xml` | 3–13 | Features de Java EE 8 (`servlet-4.0`, `jsp-2.3`, `javaMail-1.6`, `webProfile-8.0`, …) | Equivalentes de Jakarta EE 10 si se mantiene Liberty ([spring-config.md](spring-config.md#features)) |
| `setup-docker.sh` | 17, 29 | `javax.servlet-api 4.0.1`, `javax.mail-api 1.6.2` | Reemplazado por dependencias Maven `jakarta.*` |
| `setup-docker.bat` | 13, 24 | Ídem | Ídem |

## Librerías de terceros

| Librería | ¿Depende de APIs Java EE `javax.*`? | Impacto |
| --- | --- | --- |
| Spring 5.3.23 | Sí (`javax.servlet`) | Hay que subir a 6.x o superior (`jakarta.*`) |
| iBATIS 2.3.0 | No (solo `javax.sql` y `javax.naming`, Java SE) | Compatible en namespace, pero EOL y dependiente de JNDI ([B-08](../blockers.md#b-08)) |
| Jackson 1.9.13 | No | Se reemplaza por otros motivos ([B-13](../blockers.md#b-13)) |
| log4j 1.2.17 | Solo appenders opcionales (JMS, SMTP) que no se usan | Se reemplaza por otros motivos ([B-12](../blockers.md#b-12)) |

## Herramienta sugerida

- OpenRewrite: `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta` (receta `javax-to-jakarta` del taller). Validar si cubre `web.xml`; si no, ajustarlo a mano.
- Si en Fase 2 se decide reescribir los 3 servlets y el filter como controllers de Spring MVC, 20 de los 24 imports desaparecen en lugar de migrarse; solo quedarían los 5 de `javax.mail` si se conserva el código de email.
