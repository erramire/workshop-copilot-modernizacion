# Bloqueos: student-web-app

> **Sistema analizado:** `legacy/java/jakarta-ee/student-web-app` · **Fase 1 (assessment)** · 2026-10-04
>
> **Severidad:**
> - **Crítico:** impide llegar al target del workshop (Spring Boot 3 + Java 21 en JAR, en Azure Container Apps) o desplegar de forma segura.
> - **Alto:** obligatorio en la migración.
> - **Medio:** debe resolverse, pero no frena la migración.
> - **Bajo:** mejora recomendable.

## Resumen

| Categoría | Crítico | Alto | Medio | Bajo |
| --- | --- | --- | --- | --- |
| Plataforma, build y empaquetado | 2 | 2 | 2 | 2 |
| APIs deprecated o removidas | 1 | 2 | 2 | 1 |
| Persistencia y datos | 0 | 2 | 1 | 1 |
| Seguridad | 1 | 3 | 4 | 3 |
| Funcional y arquitectura | 0 | 1 | 2 | 3 |
| Calidad y pruebas | 0 | 1 | 0 | 0 |
| **Total** | **4** | **11** | **11** | **10** |

## 1. Plataforma, build y empaquetado

| ID | Severidad | Bloqueo | Evidencia | Acción |
| --- | --- | --- | --- | --- |
| PLT-01 | Crítico | Spring Framework 5.3.23 sobre `javax.*`. Spring Boot 3 necesita Spring 6, Jakarta EE 9+ y Java 17+ | `WebContent/WEB-INF/lib/spring/*-5.3.23.jar` | Upgrade mayor (ver API-01) |
| PLT-02 | Crítico | Las vistas son JSP, y **Spring Boot no soporta JSP en JAR ejecutable**, que es el packaging exigido por el workshop | 4 JSP en `WebContent/`, `InternalResourceViewResolver` | Fase 2 decide: migrar a Thymeleaf o mantener WAR |
| PLT-03 | Alto | Build con Ant sin gestión de dependencias: los JARs binarios están en git, sin versiones declarativas ni checksums | [build.xml](../legacy/java/jakarta-ee/student-web-app/build.xml), [inventory/dependencies-pom.md](inventory/dependencies-pom.md) | Crear el `pom.xml` a partir del inventario |
| PLT-04 | Alto | El classpath parece incompleto: faltan `spring-expression` y `spring-jcl`, que spring-context y spring-core 5.3 necesitan. No está claro que la línea base funcione | `WebContent/WEB-INF/lib/spring/` | Arrancar la app legacy antes de migrar para tener una línea base |
| PLT-05 | Medio | Acoplamiento con Open Liberty: DataSource y Mail por JNDI, librería compartida, context root y features de Java EE 8 | [dependencies.md §4](dependencies.md) | Llevar esa configuración a `application.yml` y variables de entorno |
| PLT-06 | Medio | El Dockerfile es single-stage, necesita el WAR compilado en el host, usa `open-liberty` con Java 17 OpenJ9 y expone 9080/9443. El workshop pide multi-stage con `eclipse-temurin:21-jre-alpine` y puerto 8080 | [Dockerfile](../legacy/java/jakarta-ee/student-web-app/Dockerfile) | Nuevo Dockerfile en Fase 3 |
| PLT-07 | Bajo | Se compila para Java 11 (`source/target` fijados en `build.xml`). No hay código incompatible con Java 17/21 | [build.xml L27](../legacy/java/jakarta-ee/student-web-app/build.xml#L27) | Subir a Java 21 |
| PLT-08 | Bajo | `setup-docker.bat` descarga Connector/J de una URL incorrecta y `curl` no tiene `-f`. Afecta a quien haga el taller en Windows | [setup-docker.bat L36](../legacy/java/jakarta-ee/student-web-app/setup-docker.bat#L36) | Desaparece con Maven; avisar a los participantes |

## 2. APIs deprecated o removidas

### `javax.*` → `jakarta.*` (cambio de namespace de Jakarta EE 9)

- **4 de 9** archivos Java importan paquetes `javax.*` afectados: **24 líneas** (`javax.servlet` ×19, `javax.mail` ×5).
- `web.xml` usa el namespace Java EE 8 y tiene `res-type javax.mail.Session`.
- Acción: refactor obligatorio. Spring Boot 3 lo requiere.
- Herramienta sugerida: receta de OpenRewrite `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta`.
- Detalle: [inventory/javax-usages.md](inventory/javax-usages.md).

### Comprobaciones de APIs eliminadas en Java 11+ y endurecidas en Java 17

| Búsqueda | Resultado |
| --- | --- |
| `sun.misc`, `java.applet`, `java.security.acl`, `com.sun.image` | 0 |
| JAXB (`javax.xml.bind`) | 0 |
| CORBA (`org.omg`, `javax.rmi.CORBA`) | 0 |
| `setAccessible(true)` | 0 |
| `javax.persistence`, `javax.validation`, `javax.ejb`, `javax.jms` | 0 |

### Bloqueos

| ID | Severidad | Bloqueo | Evidencia | Acción |
| --- | --- | --- | --- | --- |
| API-01 | Alto | Cambio de namespace `javax.*` → `jakarta.*` (detalle arriba) | [inventory/javax-usages.md](inventory/javax-usages.md) | OpenRewrite, como primer paso de Fase 3 |
| API-02 | Crítico | **iBATIS 2.3.0**, retirado en 2010: la API `com.ibatis.*` aparece en 5 clases y Spring 6 no tiene integración con él | [inventory/persistence.md](inventory/persistence.md) | Reescribir el acceso a datos (Fase 2 elige MyBatis 3, Spring Data JPA o JdbcClient) |
| API-03 | Alto | **log4j 1.2.17** (EOL en 2015, con CVEs críticas): `org.apache.log4j.Logger` en 6 clases | `StudentController`, `AddStudentController`, `StudentService`, `IndexServlet`, `AddStudentServlet`, `StudentProfileListServlet` | SLF4J + Logback |
| API-04 | Medio | **Jackson 1.x** (`org.codehaus.jackson`, EOL) | [StudentProfileListServlet.java L6](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfileListServlet.java#L6) | Jackson 2 (`com.fasterxml`) |
| API-05 | Medio | Lookups JNDI manuales (`new InitialContext()`, `dataSource type="JNDI"`), que Spring Boot embebido no ofrece | [AddStudentServlet.java L98-L99](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L98-L99), [sql-map-config.xml L9-L11](../legacy/java/jakarta-ee/student-web-app/resources/sql-map-config.xml#L9-L11) | `spring.datasource.*` y `spring.mail.*` |
| API-06 | Bajo | Cambios de comportamiento en Spring 6: el *trailing slash match* viene desactivado y `PathPatternParser` es el matcher por defecto | [inventory/controllers.md](inventory/controllers.md) | Validar las URLs con tests de caracterización |

## 3. Persistencia y datos

| ID | Severidad | Bloqueo | Evidencia | Acción |
| --- | --- | --- | --- | --- |
| DAT-01 | Alto | Las transacciones son manuales y la lógica de datos está repetida en 5 puntos: los servlets se saltan el service | [inventory/services-repositories.md](inventory/services-repositories.md) | Concentrarla en un repositorio con `@Transactional` |
| DAT-02 | Alto | El script DDL es destructivo (`DROP TABLE IF EXISTS`) y no hay herramienta de migraciones: riesgo de perder datos al desplegar | [create_table.sql L1](../legacy/java/jakarta-ee/student-web-app/database/create_table.sql#L1) | Crear un baseline con Flyway o Liquibase antes de tocar la BD |
| DAT-03 | Medio | MySQL 8.0 está fuera de soporte desde abril de 2026, y la conexión va sin TLS (`useSSL=false&allowPublicKeyRetrieval=true`). Azure Database for MySQL exige TLS | [docker-compose.yml L29](../legacy/java/jakarta-ee/student-web-app/docker-compose.yml#L29), [server-docker.env L10](../legacy/java/jakarta-ee/student-web-app/liberty_config/server-docker.env#L10) | Versión destino y TLS (Fase 2) |
| DAT-04 | Bajo | El esquema no tiene restricciones (ni `NOT NULL` ni `UNIQUE` en email), la consulta no tiene `ORDER BY` ni paginación y no se recupera el ID generado | [inventory/persistence.md](inventory/persistence.md) | Acordarlo con el cliente |

## 4. Seguridad

| ID | Severidad | Hallazgo | Evidencia | Acción |
| --- | --- | --- | --- | --- |
| SEC-01 | Crítico | **Secretos hardcoded en archivos versionados**: passwords `defaultPassword` en el `ENV` de la imagen (visibles con `docker inspect`), `password="changeit"` del SMTP, `DB_PASSWORD=studentpass` y `MYSQL_ROOT_PASSWORD` (CWE-798) | [Dockerfile L28](../legacy/java/jakarta-ee/student-web-app/Dockerfile#L28), [L30](../legacy/java/jakarta-ee/student-web-app/Dockerfile#L30), [L32](../legacy/java/jakarta-ee/student-web-app/Dockerfile#L32); [server-docker.xml L27](../legacy/java/jakarta-ee/student-web-app/liberty_config/server-docker.xml#L27); [server-docker.env L12](../legacy/java/jakarta-ee/student-web-app/liberty_config/server-docker.env#L12); [docker-compose.yml L6](../legacy/java/jakarta-ee/student-web-app/docker-compose.yml#L6), [L9](../legacy/java/jakarta-ee/student-web-app/docker-compose.yml#L9), [L31](../legacy/java/jakarta-ee/student-web-app/docker-compose.yml#L31) | Key Vault o secretos de Container Apps con Managed Identity; quitar los valores por defecto |
| SEC-02 | Alto | No hay autenticación ni autorización. `appSecurity-3.0` está activo, pero `web.xml` no tiene `security-constraint`. Cualquiera puede listar emails (PII) y crear registros | [web.xml](../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml) | Fase 2 decide (p. ej. Entra ID con Spring Security) |
| SEC-03 | Alto | El email se puede usar como relay de spam: `InternetAddress.parse(to, false)` acepta listas de direcciones, así que un POST anónimo puede enviar correo a varios destinatarios | [AddStudentServlet.java L101](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L101) | Validar que sea una única dirección y añadir rate limiting |
| SEC-04 | Alto | Ninguno de los 2 formularios POST tiene protección CSRF | [spring-add-student.jsp L53](../legacy/java/jakarta-ee/student-web-app/WebContent/spring-add-student.jsp#L53), [add_student_profile.jsp L51](../legacy/java/jakarta-ee/student-web-app/WebContent/add_student_profile.jsp#L51) | Activar CSRF (Spring Security) |
| SEC-05 | Medio | El servidor no valida `name`, `email` ni `major` | [AddStudentController.java L29-L31](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/AddStudentController.java#L29-L31), [AddStudentServlet.java L37-L39](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L37-L39) | Bean Validation (`jakarta.validation`) |
| SEC-06 | Medio | Filtración de información: se muestran mensajes de excepción al usuario | [IndexServlet.java L40](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/IndexServlet.java#L40), [StudentController.java L33](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/StudentController.java#L33), [AddStudentController.java L52-L53](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/AddStudentController.java#L52-L53), [StudentProfileListServlet.java L57](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfileListServlet.java#L57) | Mensajes genéricos y `@ControllerAdvice` |
| SEC-07 | Medio | *Log injection* (CWE-117) y datos personales en los logs a nivel INFO | [AddStudentController.java L34](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/AddStudentController.java#L34), [StudentService.java L46](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java#L46), [AddStudentServlet.java L45](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L45) | Logging parametrizado, sin PII |
| SEC-08 | Medio | Dependencias EOL con CVEs: Spring 5.3 sin soporte OSS, log4j 1.x, Jackson 1.x y Connector/J 8.0.33. Con el uso actual ninguna es explotable, pero bloquean compliance | [inventory/dependencies-pom.md](inventory/dependencies-pom.md) | Upgrade y escaneo con OWASP Dependency-Check |
| SEC-09 | Bajo | XSS latente: las JSP de formulario imprimen variables sin escapar | [spring-add-student.jsp L43](../legacy/java/jakarta-ee/student-web-app/WebContent/spring-add-student.jsp#L43), [L49](../legacy/java/jakarta-ee/student-web-app/WebContent/spring-add-student.jsp#L49); [add_student_profile.jsp L41](../legacy/java/jakarta-ee/student-web-app/WebContent/add_student_profile.jsp#L41), [L47](../legacy/java/jakarta-ee/student-web-app/WebContent/add_student_profile.jsp#L47) | Un motor de plantillas que escape por defecto |
| SEC-10 | Bajo | `CommonHttpServletFilter` (sin registrar) confía en `X-Forwarded-For` y `X-Client-IP`. Si se reactivara, permitiría falsear la IP | [CommonHttpServletFilter.java L53-L69](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/filter/CommonHttpServletFilter.java#L53-L69) | Eliminarlo o usar `server.forward-headers-strategy` |
| SEC-11 | Bajo | Superficie innecesaria: JSP accesibles fuera de `WEB-INF/` y features de Liberty sin uso (`jaxws-2.2`, `springBoot-2.0`, `localConnector-1.0`) | [server-docker.xml L3-L13](../legacy/java/jakarta-ee/student-web-app/liberty_config/server-docker.xml#L3-L13) | Desaparece con el nuevo runtime |

Checklist de seguridad de Spring legacy:

| Hallazgo típico | ¿Presente? |
| --- | --- |
| Acegi Security | No |
| Spring Security 3.x | No hay ninguna versión de Spring Security |
| Passwords con hash débil (MD5, SHA1, DES, RC4) | No; la app no gestiona usuarios |
| Credenciales en archivos versionados | **Sí** (SEC-01) |
| `csrf().disable()` | No aplica (no hay Spring Security), pero tampoco hay ninguna protección CSRF (SEC-04) |

## 5. Funcional y arquitectura

| ID | Severidad | Hallazgo | Evidencia | Acción |
| --- | --- | --- | --- | --- |
| FUN-01 | Alto | Dos flujos de alta que se comportan distinto: solo `/addStudent` envía email | [features/02](features/02-registro-estudiante.md) | **Decisión de negocio** antes de unificar |
| FUN-02 | Medio | 4 endpoints de listado, cada uno con su manejo de errores | [features/01](features/01-consulta-estudiantes.md) | Decidir qué URLs se mantienen y qué redirects de compatibilidad hacen falta |
| FUN-03 | Medio | Bugs actuales que la migración no debería "arreglar" sin acuerdo: el error del alta legacy no se ve (`errorMsg` vs `errorMessage`), un fallo de email se muestra como éxito, el enlace `/students` está roto, "Add Another Student" lleva a `/` y los errores de BD en el flujo Spring aparecen como lista vacía | [inventory/controllers.md](inventory/controllers.md) | Documentarlos en tests de caracterización y acordarlos con el cliente |
| FUN-04 | Bajo | Los mensajes flash se guardan en la sesión HTTP. Con varias réplicas en ACA hace falta afinidad de sesión o una alternativa | [AddStudentController.java L42-L53](../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/controller/AddStudentController.java#L42-L53) | Evaluarlo en Fase 2 |
| FUN-05 | Bajo | Se escribe log a un archivo local (`/logs/applog/...`), que es efímero en contenedores | [log4j.properties L11](../legacy/java/jakarta-ee/student-web-app/resources/log4j.properties#L11) | Log a stdout y Application Insights |
| FUN-06 | Bajo | Código muerto: `applicationContext-service.xml` (que apunta a un `ucm_schema.properties` inexistente), `CommonHttpServletFilter` y el `component-scan` de `coreft.dao` | [inventory/spring-config.md](inventory/spring-config.md) | Confirmarlo con el cliente y eliminarlo |

## 6. Calidad y pruebas

| ID | Severidad | Hallazgo | Acción |
| --- | --- | --- | --- |
| QA-01 | Alto | **No hay ningún test**, ni directorio de tests ni task `junit` en Ant. Sin red de seguridad no se puede demostrar que la app migrada se comporta igual | Antes de migrar, escribir tests de caracterización de los 8 handlers, incluyendo los bugs actuales (FUN-03) |
