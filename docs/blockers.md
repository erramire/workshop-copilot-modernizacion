# Bloqueos y riesgos: student-web-app

> Sistema: `legacy/java/jakarta-ee/student-web-app/`. Las rutas son relativas a esa carpeta.
> Target de referencia del workshop (`.github/copilot-instructions.md`): Spring Boot 3.x, Java 21 Temurin, Maven, **JAR ejecutable**, imagen `eclipse-temurin:21-jre-alpine` multi-stage, puerto 8080 y Azure Container Apps.
> Severidad: 🔴 crítico (impide el target o implica un riesgo de seguridad alto) · 🟠 alto · 🟡 medio · ⚪ bajo.

## Resumen

| # | Bloqueo | Categoría | Severidad |
| --- | --- | --- | --- |
| B1 | Vistas JSP incompatibles con un JAR ejecutable | Arquitectura | 🔴 |
| B2 | Recursos JNDI del servidor (DataSource y Mail) | Plataforma | 🔴 |
| B3 | Credenciales en texto plano en archivos versionados | Seguridad | 🔴 |
| B4 | Build Ant sin gestión de dependencias (OpenRewrite necesita Maven) | Build | 🟠 |
| B5 | iBATIS 2.3.0 (retirado) como capa de datos | Persistencia | 🟠 |
| B6 | Dependencias EOL con CVEs (Log4j 1.x, Jackson 1.x, Spring 5.3, Connector/J) | Seguridad | 🟠 |
| B7 | Dos pilas web con comportamiento divergente | Funcional | 🟠 |
| B8 | Sin autenticación, CSRF ni validación de entrada | Seguridad | 🟠 |
| B9 | Sin tests | Calidad | 🟠 |
| B10 | Namespace `javax.*` → `jakarta.*` (4 archivos, 24 imports) | Plataforma | 🟡 |
| B11 | Operación no preparada para la nube (logs a archivo, puertos, SMTP en localhost) | Operación | 🟡 |
| B12 | Discrepancias entre la documentación, el lab y el código | Gobierno | 🟡 |

---

## B1 · JSP incompatibles con un JAR ejecutable 🔴

- Hay 4 JSP (445 líneas) con scriptlets Java (`<% %>`, `<%! %>`) y sin JSTL: `WebContent/index.jsp`, `spring-index.jsp`, `spring-add-student.jsp` y `add_student_profile.jsp`.
- **Spring Boot no soporta JSP cuando se empaqueta como JAR ejecutable** (sección "JSP Limitations" de la documentación de Spring Boot). Solo funcionan con empaquetado WAR.
- La regla del workshop es "JAR ejecutable (no WAR)".
- Opciones para Fase 2 (aquí no se decide):
  - (a) reescribir las vistas en Thymeleaf;
  - (b) mantener el WAR con JSP, lo que contradice la regla del workshop;
  - (c) exponer una API y separar el frontend.

## B2 · Dependencia de recursos JNDI del servidor 🔴

| Recurso | Definición | Consumo |
| --- | --- | --- |
| DataSource `jdbc/StudentDB` | `liberty_config/server-docker.xml:35-41` | `resources/sql-map-config.xml:9-10` (iBATIS `dataSource type="JNDI"`) |
| Mail session `mail/StudentMailSession` | `liberty_config/server-docker.xml:22-27` | `AddStudentServlet.java:98-99` (`InitialContext.lookup`) |
| Driver MySQL | Librería compartida de Liberty (`server-docker.xml:31-33`, `Dockerfile:14`) | Classloader del servidor |

- Spring Boot con contenedor embebido no ofrece JNDI. El DataSource y el correo tienen que configurarse en Spring, con propiedades o variables de entorno.
- En Azure Container Apps no hay servidor de aplicaciones: esta configuración pasa a variables de entorno y secretos.

## B3 · Credenciales en texto plano en archivos versionados 🔴

| Archivo | Línea | Secreto |
| --- | --- | --- |
| `Dockerfile` | 28, 30, 32 | `KEYSTORE_PASSWORD`, `TRUSTED_KEYSTORE_PASSWORD` y `LTPA_KEY_PASSWORD_SFA` = `defaultPassword`. Al estar en `ENV`, quedan en las capas de la imagen |
| `docker-compose.yml` | 6, 9, 31 | `MYSQL_ROOT_PASSWORD=rootpassword`, `MYSQL_PASSWORD` / `DB_PASSWORD=studentpass` |
| `liberty_config/server-docker.env` | 12 | `DB_PASSWORD=studentpass` |
| `liberty_config/server-docker.xml` | 27 | Mail session con `user="user"` y `password="changeit"` |

Hallazgos relacionados:
- La URL JDBC usa `useSSL=false&allowPublicKeyRetrieval=true` (`docker-compose.yml:29`, `server-docker.env:10`): el tráfico a la BD va **sin TLS**.
- `docker-compose.yml` publica MySQL en el puerto 3306 del host.
- Las instrucciones del workshop prohíben secretos en texto plano y exigen Managed Identity para ACR.

## B4 · Build Ant sin gestión de dependencias 🟠

- No hay `pom.xml`. Los jars están copiados a mano en `WebContent/WEB-INF/lib/{spring,mybatis,jackson,log4j}/`, y las APIs de compilación las descarga `setup-docker.sh` con `curl` sin `-f`, que no detecta un 404.
- **Faltan jars transitivos** de Spring 5.3 (`spring-expression` y `spring-jcl`). Es un riesgo de `NoClassDefFoundError` al arrancar que no se pudo validar en runtime (ver [inventory/dependencies-pom.md](inventory/dependencies-pom.md)).
- `setup-docker.bat` usa una URL de Connector/J distinta de la del `.sh` y probablemente rota.
- El `Dockerfile` no es multi-stage: copia un WAR que hay que construir antes con Ant en el host.
- **Orden de pasos:** OpenRewrite (recipe `javax-to-jakarta`) necesita Maven o Gradle. La conversión Ant → Maven, o al menos un `pom.xml` mínimo, tiene que ir **antes** del recipe. Las instrucciones del workshop dicen "OpenRewrite como primer paso"; Fase 2 debe resolver ese orden.
- El empaquetado actual es WAR y el target es JAR.

## B5 · iBATIS 2.3.0 como capa de datos 🟠

- `ibatis-sqlmap-2.3.0.jar`: Apache retiró el proyecto en 2010 y Spring eliminó su integración con iBATIS en la versión 4.0.
- La clase `MyBatisUtil` tiene un nombre engañoso: **no es MyBatis 3**.
- Mantiene un singleton estático fuera de Spring, con transacciones manuales y un DataSource por JNDI.
- El volumen es bajo: 2 statements, 1 tabla y 1 POJO.
- Las opciones (MyBatis 3, Spring JDBC o JPA) y las equivalencias de sintaxis están en [inventory/persistence.md](inventory/persistence.md).
- **Hibernate no aplica**: no hay `.hbm.xml`, `@Entity`, `HibernateTemplate` ni `Criteria`.

## B6 · Dependencias EOL con CVEs 🟠

| Dependencia | Versión | Soporte | CVEs | Máxima | ¿Aplica al código? |
| --- | --- | --- | --- | --- | --- |
| log4j | 1.2.17 | EOL desde 2015 | 5 | 9.8 | No directamente |
| Spring Framework | 5.3.23 | Sin soporte OSS desde agosto de 2024 | 14 | Crítica | No |
| mysql-connector-j | 8.0.33 | Línea superada | 1 (CVE-2023-22102) | 8.3 | Versión afectada |
| jackson-mapper-asl | 1.9.13 | Abandonada | 1 (CVE-2019-10172) | 7.5 | Bajo |
| ibatis-sqlmap | 2.3.0 | Retirada | 0 identificadas | — | — |

El detalle por CVE está en [inventory/cve-report.md](inventory/cve-report.md). No se ejecutó un escaneo automatizado porque no hay `pom.xml`.

## B7 · Dos pilas web con comportamiento divergente 🟠

- La misma capacidad existe por servlets Java EE y por Spring MVC:
  - Listado: 4 rutas (`/`, `/studentProfileList`, `/app/` y `/app/students`).
  - Alta: 2 rutas (`/addStudent` y `/app/add-student`).
- **Divergencias:**
  - El correo de bienvenida solo se envía por `/addStudent`.
  - El manejo de errores es distinto en cada ruta.
  - Ante un parámetro ausente, una ruta responde 400 y la otra inserta `NULL`.
- Los servlets se saltan `StudentService` y acceden a los datos directamente.
- `IndexServlet` en `/` reemplaza al *default servlet* del contenedor.
- Para Fase 2: decidir qué rutas se conservan (no se sabe si hay consumidores externos de `/addStudent` o `/studentProfileList`) y qué comportamiento es el correcto. Ver [features/](features/).

## B8 · Sin autenticación, CSRF ni validación de entrada 🟠

| Hallazgo | Evidencia | Riesgo |
| --- | --- | --- |
| No hay autenticación ni autorización. `appSecurity-3.0` está habilitado en Liberty pero no se usa | `web.xml` sin `security-constraint`; no hay Spring Security | Cualquiera lee los datos personales y crea registros |
| No hay protección CSRF | Formularios POST en `spring-add-student.jsp:53` y `add_student_profile.jsp:51` | CSRF |
| No hay validación en el servidor | No hay Bean Validation. El email solo se valida con HTML5 | Datos inválidos y errores de BD |
| XSS latente | Salida sin escapar en `spring-add-student.jsp:43,49` y `add_student_profile.jsp:41,47` | XSS si cambia el flujo de mensajes |
| Detalle técnico en mensajes de error (CWE-209) | `StudentController.java:33,50`, `AddStudentController.java:53`, `IndexServlet.java:40`, `StudentProfileListServlet.java:57` | Divulgación de información |
| Datos personales en logs y log injection (CWE-117) | `AddStudentController.java:34`, `StudentService.java:46`, `AddStudentServlet.java:45,58,96,105` | Privacidad y falsificación de logs |
| Correo saliente a cualquier dirección desde un endpoint público | `AddStudentServlet.java:60` | Abuso para spam |
| Se confía en `X-Forwarded-For` / `X-Client-IP` | `CommonHttpServletFilter.java:53-68` (hoy no está registrado) | Falsificación de la IP si se activa |

Verificaciones sin hallazgos:
- No se usan algoritmos débiles (MD5, SHA-1, DES, RC4).
- No hay Acegi ni Spring Security antiguo.
- No hay SQL dinámico: iBATIS usa `#param#`, que genera `PreparedStatement`.

## B9 · Sin tests 🟠

- No hay tests ni frameworks de test (ni JUnit ni Mockito), y `build.xml` no tiene target de test.
- Sin tests de caracterización no hay forma de demostrar la paridad funcional después de migrar. Cada feature en `docs/features/` incluye escenarios de paridad como punto de partida.
- El Lab 02 menciona "JUnit 4 → JUnit 5", pero no aplica: no hay tests que migrar.

## B10 · Namespace `javax.*` → `jakarta.*` 🟡

- Hay 4 archivos Java con 24 imports que cambian (19 de `javax.servlet` y 5 de `javax.mail`) y 2 imports de `javax.naming` que no cambian.
- `web.xml` cambia el namespace y el `res-type javax.mail.Session`.
- Spring 5.3 está compilado contra `javax.servlet`, así que el cambio de namespace obliga a subir a Spring 6.x en el mismo paso.
- El detalle está en [inventory/javax-usages.md](inventory/javax-usages.md).

### APIs eliminadas en Java 11+ y encapsulación fuerte de Java 17+

| Búsqueda | Resultado |
| --- | --- |
| `sun.misc`, `java.applet`, `java.security.acl`, `com.sun.image` | 0 |
| `javax.xml.bind` (JAXB) | 0 |
| CORBA (`org.omg`, `javax.rmi.CORBA`) | 0 |
| `setAccessible(true)` | 0 |

El código propio no tiene bloqueos para Java 21. Hoy compila con `source/target 11` (`build.xml:27`) y se ejecuta en Java 17 OpenJ9 (`Dockerfile:1`).

## B11 · Operación no preparada para la nube 🟡

| Hallazgo | Evidencia |
| --- | --- |
| Log a un archivo con ruta absoluta (`/logs/applog/osap/coreft1617/coreft1617sfa.log`). En un contenedor, ese sistema de archivos es efímero | `resources/log4j.properties:10-15` |
| `System.out` / `System.err` en lugar del logger | `MyBatisUtil.java:15,18,22` |
| Puertos 9080 y 9443, cuando el target usa 8080 | `Dockerfile:20`, `server-docker.xml:16` |
| SMTP en `localhost:25` | `server-docker.xml:23,25` |
| No hay endpoint de health check | — |
| Los enlaces absolutos de las JSP asumen el context root `/` | Vistas JSP |
| `create_table.sql` empieza con `DROP TABLE IF EXISTS` y no hay migraciones de esquema | `database/create_table.sql:1` |
| Configuración que no se usa: variables `SESSION_COOKIE_NAME`, `CLONE_ID`, `SESSION_TIMEOUT`, `LTPA_SFA_EXPIRATION` y `DEV_LIB_DIR`, y las features `jaxws-2.2` y `springBoot-2.0` | `server-docker.env`, `server-docker.xml` |

## B12 · Discrepancias entre la documentación, el lab y el código 🟡

| Fuente | Qué dice | Qué hay en el código |
| --- | --- | --- |
| `README.md`, `doc/architecture.md`, JSP | Spring 5.3.39 | Los jars empaquetados son **5.3.23** |
| `README.md` y `doc/getting-started.md` | Java 17+ y Java 11+, respectivamente | Compila con source/target 11 y se ejecuta en Java 17 |
| `doc/architecture.md` | "REST API", "JSON API", "CRUD operations" | No hay endpoints REST (el JSON va embebido en HTML), y solo existen Create y Read |
| `labs/lab-02-java/README.md` | `spring-config.xml`, `mvc-core-config.xml` | `applicationContext.xml` y `spring-servlet.xml` |
| `labs/lab-02-java/README.md` | `HibernateTemplate`, mappings XML de Hibernate, tests JUnit 4 | No hay Hibernate ni tests. La persistencia es iBATIS 2 |
| `labs/lab-02-java/README.md` | "30+ archivos" con `javax.*` | 4 archivos Java |
| `labs/lab-02-java/README.md` | PetClinic y `cd legacy/java && ./mvnw spring-boot:run` | Es una app de estudiantes en `legacy/java/jakarta-ee/student-web-app/` y no hay `mvnw` |
| `labs/lab-02-java/README.md` (título) y handoff del agente | Jakarta EE 10 + Spring Framework 6.2 | `.github/copilot-instructions.md` pide Spring Boot 3 en JAR y Container Apps. **El target tiene que decidirse en Fase 2** |

## Hallazgos de calidad (no bloqueantes) ⚪

- **Código muerto:**
  - `CommonHttpServletFilter` (no está registrado y su atributo no se lee).
  - `resources/applicationContext-service.xml` (no se carga, y el `.properties` que referencia no existe).
  - El `component-scan` del paquete `dao`, que no existe.
  - El import sin uso de `StudentProfile` en `AddStudentController`.
- **Bugs funcionales:**
  - El servlet guarda `errorMsg` y la JSP lee `errorMessage`, así que el error nunca se muestra.
  - El enlace a `/students` está roto.
  - `getAllStudents()` oculta los errores de BD.
  - El log menciona un "HelloServlet" que no existe.
- **Recursos:** el `Reader` no se cierra en `MyBatisUtil.java:16`.
- **Duplicación:** el escape HTML está implementado 3 veces, y `StudentController.index` es idéntico a `listStudents`.
