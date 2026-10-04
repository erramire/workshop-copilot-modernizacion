# Bloqueos — Student Web App (Fase 1)

Ruta base: `legacy/java/jakarta-ee/student-web-app/`. Las referencias `archivo:línea` son relativas a esa ruta.

**Severidad:** 🔴 Crítico (requiere rediseño) · 🟠 Alto (trabajo significativo u obligatorio) · 🟡 Medio · 🟢 Bajo

## Resumen

| ID | Categoría | Bloqueo | Severidad |
| --- | --- | --- | --- |
| [B-01](#b-01) | Build | Ant + JARs versionados, sin gestor de dependencias | 🟠 |
| [B-02](#b-02) | Build | El WAR no incluye `spring-jcl` ni `spring-expression` (a validar) | 🟠 |
| [B-03](#b-03) | Empaquetado | JSP incompatible con JAR ejecutable de Spring Boot | 🟠 |
| [B-04](#b-04) | Runtime | Acoplamiento a Open Liberty (WAR, features, puertos 9080/9443) | 🟡 |
| [B-05](#b-05) | Entorno | El devcontainer no puede construir la línea base (falta Ant) | 🟡 |
| [B-06](#b-06) | Target | Spring Boot 3.5 / Spring Framework 6.2 sin soporte OSS | 🟠 |
| [B-07](#b-07) | Jakarta EE | `javax.*` → `jakarta.*` acoplado al upgrade de Spring | 🟠 |
| [B-08](#b-08) | Persistencia | iBATIS 2.3.0 con DataSource JNDI | 🔴 |
| [B-09](#b-09) | Persistencia | Transacciones manuales y errores silenciosos | 🟡 |
| [B-10](#b-10) | Integraciones | Email por JNDI + SMTP `localhost:25` | 🟠 |
| [B-11](#b-11) | Integraciones | DataSource JNDI y JDBC sin TLS | 🟠 |
| [B-12](#b-12) | APIs | log4j 1.x usado directamente en 6 clases | 🟠 |
| [B-13](#b-13) | APIs | Jackson 1.x (codehaus) | 🟡 |
| [B-14](#b-14) | Configuración | Configuración muerta o huérfana | 🟢 |
| [B-15](#b-15) | Configuración | Logging a archivo en ruta absoluta | 🟡 |
| [B-16](#b-16) | Calidad | 0 tests, lógica duplicada y bugs funcionales | 🟠 |
| [B-17](#b-17) | Datos | MySQL 8.0 EOL, script destructivo, sin migraciones | 🟡 |
| [S-01…S-13](#g-seguridad) | Seguridad | Ver sección G | 🔴 a 🟢 |

---

## A. Plataforma, build y empaquetado

<a id="b-01"></a>
### B-01 · Build Ant sin gestor de dependencias 🟠

- `build.xml:27` compila con `source="11" target="11"`; `build.properties` define `java.source`/`java.target` pero el build no los usa.
- El classpath combina `lib/`, `WebContent/WEB-INF/lib/**`, `compile-lib/` y `${user.home}/lib/dev`. `lib/` y `~/lib/dev` no existen en el repo: el build depende del entorno del desarrollador.
- Las APIs de compilación (`javax.servlet-api 4.0.1`, `javax.mail-api 1.6.2`) se descargan con `curl -L` en `setup-docker.sh`, sin `-f`: un 404 genera un JAR corrupto sin fallar. `setup-docker.bat` usa una URL incorrecta para Connector/J.
- Sin targets de test ni generación de código (no hay JAXB, XMLBeans ni WSDL que regenerar con `jakarta.*`).
- **Acción:** convertir a Maven con wrapper `mvnw` (el lab lo invoca) y versiones gestionadas por el BOM del framework elegido.

<a id="b-02"></a>
### B-02 · El WAR no incluye `spring-jcl` ni `spring-expression` (a validar) 🟠

- `build.xml` empaqueta solo los 6 JARs de `WEB-INF/lib/spring/` (aop, beans, context, core, web, webmvc 5.3.23).
- `spring-core` 5.3 depende de `spring-jcl` (`org.apache.commons.logging`), y `spring-context`/`spring-webmvc` de `spring-expression`. Si el servidor no los provee, el arranque falla con `NoClassDefFoundError`.
- **Acción:** validar con un despliegue de la línea base. Con Maven se resuelve solo (dependencias transitivas).

<a id="b-03"></a>
### B-03 · JSP incompatible con JAR ejecutable 🟠

- 4 vistas JSP con scriptlets (445 LOC, mayormente HTML/CSS) resueltas por `InternalResourceViewResolver` (`prefix="/"`, `suffix=".jsp"`).
- La regla del taller exige **JAR ejecutable**, y Spring Boot documenta que las JSP **no funcionan en un JAR ejecutable** (solo con WAR).
- **Acción (Fase 2):** reescribir las vistas en Thymeleaf (esfuerzo bajo) o documentar en un ADR una excepción (WAR ejecutable).

<a id="b-04"></a>
### B-04 · Acoplamiento a Open Liberty 🟡

- `Dockerfile:1`: `open-liberty:25.0.0.7-kernel-slim-java17-openj9`. No es multi-stage: copia `dist/OpenLibertyApp.war` y `mysql-connector/*.jar` construidos fuera de Docker. `Dockerfile:20` expone 9080/9443 (target del taller: 8080).
- `server-docker.xml`: 11 features, 4 sin uso en el código (`jaxws-2.2`, `springBoot-2.0`, `appSecurity-3.0`, `localConnector-1.0`); `classloader delegation="parentLast"`.
- **Acción:** si el target es Spring Boot, Liberty desaparece y los recursos del servidor (DataSource, MailSession, pool) pasan a propiedades externas. Detalle en [inventory/spring-config.md](inventory/spring-config.md#features).

<a id="b-05"></a>
### B-05 · El devcontainer no puede construir la línea base 🟡

- `.devcontainer/install-java.sh` instala Temurin 8, Temurin 21 y Maven, pero **no Apache Ant**.
- Temurin 8 no compila `source 11`; Temurin 21 sí (con advertencias).
- **Impacto:** dentro de Codespaces no se puede validar [B-02](#b-02) ni capturar el comportamiento actual con tests de caracterización.
- **Acción:** instalar `ant` en el devcontainer o asumir que la línea base no se ejecuta.

<a id="b-06"></a>
### B-06 · El target propuesto no tiene soporte OSS 🟠

| Versión | Soporte OSS | Soporte comercial |
| --- | --- | --- |
| Spring Boot 3.5 / Spring Framework 6.2 | Terminó el 30-jun-2026 | Hasta 30-jun-2032 |
| Spring Boot 4.0 | Hasta 31-dic-2026 | Hasta 31-dic-2027 |
| Spring Boot 4.1 / Spring Framework 7.0 (Jakarta EE 11) | Hasta 31-jul-2027 | Hasta 31-jul-2028 |

Fuente: [endoflife.date](https://endoflife.date/spring-boot), consultado el 2026-10-04.

- `copilot-instructions.md` fija "Spring Boot 3.x" y el lab "Spring Boot 3.5".
- **Acción (Fase 2):** decidir en un ADR entre 3.5 con soporte comercial y 4.x OSS (con cambios adicionales, p. ej. Jackson 3 por defecto y Jakarta EE 11).

## B. Namespace Jakarta EE

<a id="b-07"></a>
### B-07 · `javax.*` → `jakarta.*` acoplado al upgrade de Spring 🟠

- 4 de 9 archivos Java y 24 imports (`javax.servlet` ×19, `javax.mail` ×5). `javax.naming` (×2) y `javax.sql` no cambian (Java SE).
- `web.xml`: namespace `xmlns.jcp.org` / `web-app_4_0.xsd` y `res-type javax.mail.Session`.
- APIs de compilación y features de Liberty en versión Java EE 8.
- Spring 5.3 solo funciona con `javax.*` y Spring 6+ solo con `jakarta.*`: el namespace change y el upgrade de Spring van en el mismo paso.
- **Herramienta sugerida:** OpenRewrite `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta` (validar si cubre `web.xml`).
- Detalle en [inventory/javax-usages.md](inventory/javax-usages.md).

## C. Persistencia

<a id="b-08"></a>
### B-08 · iBATIS 2.3.0 con DataSource JNDI 🔴

- `MyBatisUtil` (nombre engañoso: es iBATIS 2, no MyBatis 3) crea un `SqlMapClient` estático en un bloque `static` a partir de `sql-map-config.xml`, que obtiene el DataSource por JNDI (`jdbc/StudentDB`).
- iBATIS se retiró en 2010; Spring eliminó `SqlMapClientTemplate`/`SqlMapClientFactoryBean` en 4.0, y Spring Boot embebido no expone JNDI.
- 5 puntos de llamada en 4 clases (3 servlets + `StudentService`) y 2 statements.
- **Acción (Fase 2):** elegir entre MyBatis 3 (`mybatis-spring-boot-starter`, migración casi 1:1 del XML: `#name#` → `#{name}`), Spring Data JPA o `JdbcClient`. Detalle en [inventory/persistence.md](inventory/persistence.md).

<a id="b-09"></a>
### B-09 · Transacciones manuales y errores silenciosos 🟡

- `startTransaction`/`commitTransaction`/`endTransaction` manuales en `StudentService.saveStudent` y `AddStudentServlet.doPost`; no hay `@Transactional` ni transaction manager.
- `StudentService.getAllStudents` (`service/StudentService.java:28-31`) traga las excepciones y devuelve lista vacía: con la BD caída, la UI de Spring muestra "No student profiles found.".
- **Acción:** `@Transactional` y manejo de errores centralizado (`@ControllerAdvice`).

## D. Integraciones

<a id="b-10"></a>
### B-10 · Email por JNDI + SMTP `localhost:25` 🟠

- `AddStudentServlet.java:99` hace lookup de `java:comp/env/mail/StudentMailSession`.
- `server-docker.xml:22-27`: `host="localhost"`, `port="25"`, `user="user"`, `password="changeit"`, `mailSessionID="SendGridMailSession"` (sugiere SendGrid en algún entorno real).
- `localhost` no es válido en Azure, la salida por el puerto 25 está bloqueada en la mayoría de suscripciones de Azure y Spring Boot embebido no tiene JNDI.
- **Acción:** `JavaMailSender` (`spring-boot-starter-mail`, `jakarta.mail`) o SDK de Azure Communication Services, con credenciales en Key Vault o secretos de ACA. Ver [F-03](features/03-email-bienvenida.md).

<a id="b-11"></a>
### B-11 · DataSource JNDI y JDBC sin TLS 🟠

- `jdbc/StudentDB` está definido en Liberty con `${env.JDBC_URL}`, `${env.DB_USER}` y `${env.DB_PASSWORD}`; pool de 2 a 10 conexiones.
- La URL JDBC usa `useSSL=false&allowPublicKeyRetrieval=true` (`docker-compose.yml:29`, `liberty_config/server-docker.env:10`).
- El driver es una librería compartida del servidor (fuera del WAR).
- **Acción:** `spring.datasource.*` con HikariCP, driver como dependencia `runtime`, TLS obligatorio (Azure Database for MySQL lo exige) y evaluar autenticación passwordless con Managed Identity.

## E. APIs deprecated o removidas

<a id="b-12"></a>
### B-12 · log4j 1.x usado directamente 🟠

- `org.apache.log4j.Logger` se importa en 6 clases: los 3 servlets, los 2 controllers y `StudentService`. `MyBatisUtil` usa `System.out/err` y `printStackTrace`.
- log4j 1.2.17 tiene EOL desde 2015 y CVEs críticas (ver [dependencias](inventory/dependencies-pom.md#log4j-1217)). **No** es vulnerable a Log4Shell (CVE-2021-44228 solo afecta a log4j 2).
- **Acción:** SLF4J + Logback (default de Spring Boot). OpenRewrite tiene recetas de migración (p. ej. `org.openrewrite.java.logging.slf4j.Log4j1ToSlf4j1`).

<a id="b-13"></a>
### B-13 · Jackson 1.x (codehaus) 🟡

- `org.codehaus.jackson.map.ObjectMapper` se usa en `StudentProfileListServlet` (1 clase).
- Spring dejó de soportar Jackson 1 en 4.1, así que `<mvc:annotation-driven/>` no registra conversores JSON: **no existe una API JSON real**, aunque la documentación del sample la menciona.
- **Acción:** Jackson 2 (`com.fasterxml`, Spring Boot 3) o Jackson 3 (`tools.jackson`, Spring Boot 4).

### Otras APIs revisadas (Java 11 → 21)

| Búsqueda | Ocurrencias |
| --- | --- |
| JAXB (`javax.xml.bind`) | 0 |
| CORBA (`org.omg`, `javax.rmi.CORBA`) | 0 |
| `sun.misc`, `java.applet`, `java.security.acl`, `com.sun.image` | 0 |
| `setAccessible(true)` | 0 |
| `HibernateTemplate`, `*DaoSupport`, `SqlMapClientTemplate` | 0 |

El salto de Java 11 a 21 es de bajo riesgo en el código propio.

## F. Configuración

<a id="b-14"></a>
### B-14 · Configuración muerta o huérfana 🟢

| Elemento | Problema |
| --- | --- |
| `resources/applicationContext-service.xml` | No lo carga nadie. Referencia `ucm_schema.properties`, que no existe: si se cargara, el arranque fallaría |
| `WebContent/WEB-INF/applicationContext.xml:12` | `component-scan` sobre `org.sample.azure.student.coreft.dao`, paquete inexistente |
| `filter/CommonHttpServletFilter.java` | No está registrado en `web.xml` ni con `@WebFilter` |
| `resource-ref jdbc/StudentDB` en `web.xml` | No se usa: iBATIS busca el nombre global `jdbc/StudentDB` |
| `liberty_config/server-docker.env` | `SESSION_COOKIE_NAME`, `CLONE_ID`, `SESSION_TIMEOUT`, `LTPA_SFA_EXPIRATION` y `DEV_LIB_DIR` no se referencian en `server-docker.xml` |
| `Dockerfile` | `KEYSTORE_*`, `TRUSTED_KEYSTORE_*`, `LTPA_KEY_*` y `JDBC_DRIVER_CLASS` no se referencian en `server-docker.xml` (validar si la imagen base los usa) |

- **Acción:** no migrar; confirmar con el cliente antes de eliminar.

<a id="b-15"></a>
### B-15 · Logging a archivo en ruta absoluta 🟡

- `resources/log4j.properties:11` define un `RollingFileAppender` a `/logs/applog/osap/coreft1617/coreft1617sfa.log` (100 MB × 10).
- En el contenedor (usuario 1001) esa ruta no existe o no es escribible. En ACA los logs deben ir a stdout (Log Analytics).
- **Acción:** logging solo a consola, con correlación en Application Insights.

## G. Seguridad

| ID | Severidad | Hallazgo | Ubicación | Riesgo | Acción |
| --- | --- | --- | --- | --- | --- |
| S-01 | 🔴 | Secretos en archivos versionados y en la imagen | `Dockerfile:28,30,32` (`ENV ...PASSWORD=defaultPassword`, quedan en las capas de la imagen); `server-docker.xml:27` (`password="changeit"`); `server-docker.env:12`; `docker-compose.yml:6,9,31` | Exposición de credenciales | Key Vault, secretos de ACA o Managed Identity; nunca `ENV` con secretos |
| S-02 | 🟠 | Sin autenticación ni autorización | `web.xml` sin `security-constraint`; sin Spring Security | Cualquiera lee PII (emails) y crea registros | Definir el modelo de acceso en Fase 2 (p. ej. Entra ID) |
| S-03 | 🟠 | Envío de email a cualquier dirección sin autenticación | `AddStudentServlet.java:60,95-106` | Abuso como relay de spam, daño a la reputación del dominio | Autenticación, rate limiting o verificación |
| S-04 | 🟠 | JDBC sin TLS y con `allowPublicKeyRetrieval=true` | `docker-compose.yml:29`, `server-docker.env:10` | MITM; Azure Database for MySQL rechaza la conexión | TLS obligatorio |
| S-05 | 🟠 | Dependencias EOL con CVEs conocidos | log4j 1.2.17, Jackson 1.9.13, Spring 5.3.23, Connector/J 8.0.33 | Ver [dependencias](inventory/dependencies-pom.md#cves-conocidas) | Reemplazar o actualizar |
| S-06 | 🟡 | Sin protección CSRF | POST `/addStudent`, POST `/app/add-student` | Hoy limitado (sin sesión autenticada); será relevante al agregar autenticación | CSRF de Spring Security |
| S-07 | 🟡 | XSS latente: salida sin escapar | `add_student_profile.jsp:41,47`; `spring-add-student.jsp:43,49` | Hoy no se alcanza (los atributos no llegan a esas vistas), pero `successMessage` contiene el `name` ingresado | Escapado automático (Thymeleaf `th:text`) |
| S-08 | 🟡 | Mensajes de excepción mostrados al usuario (CWE-209) | `StudentController.java:33,50`; `IndexServlet.java:40`; `AddStudentController.java:53`; `StudentProfileListServlet.java:57` | Divulgación de detalles internos (SQL, JNDI) | Mensajes genéricos + detalle solo en logs |
| S-09 | 🟡 | PII y entrada sin sanitizar en logs (CWE-532, CWE-117) | `AddStudentServlet.java:45,58,96,105`; `AddStudentController.java:34,41,45`; `StudentService.java:46,64` | Emails en logs; log forging con CRLF | Enmascarar PII; logging estructurado |
| S-10 | 🟡 | Sin validación de entrada en servidor | `AddStudentServlet.java:37-39`; `AddStudentController.java:29-31` | Datos inválidos o `NULL`; fallos por longitud > 255 | Bean Validation (`jakarta.validation`) |
| S-11 | 🟢 | JSP accesibles directamente | `WebContent/*.jsp` (fuera de `WEB-INF`) | Vistas expuestas sin controlador | Plantillas fuera del contexto público |
| S-12 | 🟢 | Confianza en `X-Forwarded-For`/`X-Client-IP` sin validar | `filter/CommonHttpServletFilter.java` (no registrado) | Spoofing de IP si se activa | `server.forward-headers-strategy` + proxies de confianza |
| S-13 | 🟢 | `IndexServlet` mapeado a `/` captura cualquier URL | `web.xml:42` | Enmascara los 404 y dificulta el monitoreo | Mapeos explícitos |

**Sin hallazgos:** algoritmos débiles (MD5, SHA1, DES, RC4), Acegi Security, Spring Security legacy, `csrf().disable()`, SQL injection (iBATIS usa `#param#`, no `$param$`).

## H. Calidad

<a id="b-16"></a>
### B-16 · Sin tests, lógica duplicada y bugs funcionales 🟠

- 0 tests y sin framework de pruebas.
- Cada caso de uso tiene 2 o 3 implementaciones (servlets vs Spring MVC) con comportamiento divergente:
  - El email de bienvenida solo se envía por `/addStudent` (servlet).
  - El servlet setea `errorMsg` (`AddStudentServlet.java:90`) pero la JSP lee `errorMessage`: el usuario nunca ve el error.
  - Link roto en `add_student_profile.jsp:31` (`/students`, que termina en `IndexServlet`).
  - `/app/` y `/app/students` son idénticos.
- La documentación del sample es inexacta: README y JSPs dicen Spring 5.3.39 (el real es 5.3.23); `doc/architecture.md` describe una "REST API" con JSON y "MyBatis", que no existen.
- **Acción:** tests de caracterización de las 3 features antes de migrar; consolidar en un solo camino.

## I. Datos

<a id="b-17"></a>
### B-17 · Base de datos 🟡

- MySQL 8.0 tiene EOL desde abril de 2026. Alternativas LTS: 8.4 (soporte premier hasta abril de 2029) o 9.7 (hasta abril de 2031).
- `database/create_table.sql` empieza con `DROP TABLE IF EXISTS` (destructivo) y no hay herramienta de migraciones (Flyway, Liquibase).
- Todas las columnas son nullable, sin unicidad de email ni índices adicionales.
