# Resumen del Assessment Spring legacy: Student Web App

> **Fase 1 — Assessment** · Fecha: 2026-10-04 · Agente: `@spring-legacy-assessment`
>
> Sistema analizado: `legacy/java/jakarta-ee/student-web-app/` (sample de [Azure-Samples/java-migration-copilot-samples](https://github.com/Azure-Samples/java-migration-copilot-samples)). El código legacy no se modificó.

## Alcance y precondiciones

- `legacy/java/` es un clon completo del repositorio de samples con 7 proyectos. Según `.github/copilot-instructions.md` y `labs/lab-02-java/README.md`, el sistema objetivo es **`jakarta-ee/student-web-app`**. Quedan fuera de alcance `asset-manager`, `mi-sql-public-demo`, `rabbitmq-sender`, `todo-web-api-use-oracle-db`, `Malshinon` y `ContosoUniversity` (.NET).
- No existe `.copilot-project.yml` (`legacy_tech: java`, `legacy_lang: spring-legacy`); el análisis se hizo igual con el alcance anterior.
- No hay `pom.xml` ni `build.gradle`: el build es Ant (`build.xml`).
- No se ejecutó la aplicación ni un escáner de dependencias: los CVEs se identifican por versión (ver [pendientes](#pendientes-antes-de-cerrar-la-fase-1)).

## Inventario inicial

- **Build tool:** Ant (`build.xml`, target por defecto `war`), JARs versionados en `WebContent/WEB-INF/lib/`
- **Java version:** 11 (`source`/`target` en `build.xml`); runtime Java 17 (OpenJ9) en la imagen Docker
- **Spring version:** 5.3.23 según los JARs (README y JSPs dicen 5.3.39: discrepancia)
- **Struts version:** no usa
- **Hibernate version:** no usa
- **Servidor target:** Open Liberty 25.0.0.7 (`webProfile-8.0`, Java EE 8), WAR en context-root `/`
- **ORM principal:** iBATIS 2.3.0 SQL Maps (XML) sobre DataSource JNDI `jdbc/StudentDB` (MySQL)
- **Frontend:** JSP con scriptlets (sin JSTL ni EL)

### Banderas rojas

| Bandera | Detalle |
| --- | --- |
| Spring 5.3.x sin soporte OSS | Terminó el 31-ago-2024. La versión real (5.3.23) está 16 parches detrás del último OSS (5.3.39) |
| iBATIS 2.3.0 | Proyecto retirado en 2010; Spring eliminó su integración en 4.0 |
| log4j 1.2.17 | EOL desde 2015, con CVEs críticas sin parche |
| Jackson 1.9.13 (codehaus) | Abandonado desde 2013, con CVE-2019-10172 |
| MySQL 8.0 | EOL desde abril de 2026 |
| Java 11 | Sin bloqueo: no usa APIs removidas (JAXB, CORBA, `sun.misc`) |

## Stack detectado

| Capa | Tecnología |
| --- | --- |
| Presentación | 3 servlets clásicos (`web.xml`) + Spring MVC (`DispatcherServlet` en `/app/*`) + 4 JSP |
| Servicio | 1 `@Service` (`StudentService`), usado solo por Spring MVC |
| Datos | Utilitario estático `MyBatisUtil` → iBATIS `SqlMapClient` → JNDI `jdbc/StudentDB` |
| Integraciones | SMTP vía JNDI `mail/StudentMailSession` (JavaMail 1.6) |
| Transversal | log4j 1.2.17, Jackson 1.9.13 |
| Runtime | Open Liberty + MySQL 8.0 (`docker-compose.yml`) |

## Métricas

| Métrica | Valor |
| --- | --- |
| LOC Java | 682 (9 archivos) |
| LOC JSP | 445 (4 archivos) |
| Archivos de configuración XML | 8 (`web.xml`, 3 Spring, 2 iBATIS, `server-docker.xml`, `build.xml`) |
| Controllers Spring MVC | 2 (4 endpoints) |
| Servlets propios | 3 (4 handlers) |
| Filters | 1, no registrado (código muerto) |
| Services | 1 |
| Repositories/DAOs | 0 (acceso a datos desde 4 clases vía `MyBatisUtil`) |
| Entidades/modelos | 1 POJO (`StudentProfile`), 1 tabla (`student_profiles`) |
| Statements SQL | 2 (`listStudent`, `addStudent`) |
| Tests existentes | **0** (sin framework de tests) |
| Componentes Spring: anotaciones vs XML | 75% anotaciones (3) / 25% XML (1 bean activo) |
| Archivos afectados por `javax.*` → `jakarta.*` | 4 Java (24 imports) + `web.xml` + features de Liberty |

## Features funcionales

| ID | Feature | Archivo |
| --- | --- | --- |
| F-01 | Listado de perfiles de estudiantes | [features/01-listado-perfiles-estudiantes.md](features/01-listado-perfiles-estudiantes.md) |
| F-02 | Alta de perfil de estudiante | [features/02-alta-perfil-estudiante.md](features/02-alta-perfil-estudiante.md) |
| F-03 | Email de bienvenida | [features/03-email-bienvenida.md](features/03-email-bienvenida.md) |

## CVEs críticas detectadas

| Componente | CVE | Severidad | ¿Explotable en esta app? |
| --- | --- | --- | --- |
| log4j 1.2.17 | CVE-2019-17571, CVE-2022-23305 | Crítica | No directamente (los appenders afectados no están configurados), pero no hay parche posible |
| spring-web 5.3.23 | CVE-2016-1000027 | Crítica | No (no usa HttpInvoker); los escáneres la reportan igual |
| spring-web 5.3.23 | CVE-2024-22243 / 22259 / 22262 | Alta | Baja probabilidad |
| mysql-connector-j 8.0.33 | CVE-2023-22102 | Alta | Baja-media |
| jackson-mapper-asl 1.9.13 | CVE-2019-10172 | Alta | Baja (solo serializa) |

Detalle completo en [inventory/dependencies-pom.md](inventory/dependencies-pom.md).

## Bloqueos top-5

1. **Capa de datos iBATIS 2 + JNDI** ([B-08](blockers.md#b-08)): sin soporte en Spring 4+ ni en Spring Boot. Hay que reescribirla (2 statements, 5 puntos de llamada).
2. **JSP + JAR ejecutable** ([B-03](blockers.md#b-03)): la regla del taller exige JAR y Spring Boot no soporta JSP en JAR ejecutable. Hay que reescribir 4 vistas o documentar una excepción.
3. **Namespace `javax.*` → `jakarta.*` acoplado al upgrade de Spring** ([B-07](blockers.md#b-07)): 24 imports en 4 archivos, `web.xml`, APIs de compilación y features de Liberty. Spring 5.3 no corre sobre Jakarta, así que el cambio tiene que ser atómico.
4. **Integraciones JNDI (DataSource y Mail con SMTP `localhost`)** ([B-10](blockers.md#b-10), [B-11](blockers.md#b-11)): no existen en Spring Boot embebido ni en Azure Container Apps.
5. **Seguridad** ([sección G](blockers.md#g-seguridad)): secretos versionados (incluidos `ENV` del Dockerfile), sin autenticación, envío de email a direcciones arbitrarias, XSS latente y dependencias EOL con CVEs.

## Hallazgos que condicionan la Fase 2

- **El target del taller ya no tiene soporte OSS.** Spring Boot 3.5 y Spring Framework 6.2 terminaron su soporte OSS el 30-jun-2026 (queda soporte comercial hasta 2032). Spring Boot 4.1 (Spring Framework 7.0, Jakarta EE 11) tiene soporte OSS hasta el 31-jul-2027. `copilot-instructions.md` fija "Spring Boot 3.x": decidirlo explícitamente en un ADR ([B-06](blockers.md#b-06)).
- **Caminos duplicados con comportamiento divergente.** Cada caso de uso tiene una versión servlet y una Spring MVC. El email de bienvenida solo se envía por el servlet y los errores se manejan distinto. Hay que decidir qué URLs y comportamientos se conservan.
- **Sin red de seguridad.** No hay tests: se necesitan tests de caracterización antes de tocar código.
- **No se puede construir la línea base en Codespaces.** El devcontainer no instala Apache Ant ([B-05](blockers.md#b-05)).
- **Puertos.** Liberty expone 9080/9443; el target del taller es 8080.

## Recomendación para Fase 2

La Fase 1 no decide el target. Validar con el cliente o facilitador:

| Decisión | Opciones a evaluar | Restricción conocida |
| --- | --- | --- |
| Framework/runtime | Spring Boot 3.5 (soporte comercial) · Spring Boot 4.x (OSS) · Quarkus · Liberty + Jakarta EE 10 | `copilot-instructions.md`: Spring Boot 3.x, JAR ejecutable, puerto 8080 |
| Capa de datos | MyBatis 3 (conserva el SQL en XML) · Spring Data JPA · `JdbcClient` | Sin JNDI en el target |
| Vistas | Thymeleaf · WAR ejecutable con JSP | JAR ejecutable no soporta JSP |
| URLs legacy | Conservar, redirigir (301) o eliminar los caminos servlet | Duplicidad funcional |
| Email | `JavaMailSender` + SMTP de Azure Communication Services o SendGrid · SDK de ACS · envío asíncrono | Hoy es síncrono y solo en el camino servlet |
| Base de datos | MySQL 8.4 LTS (Azure Database for MySQL Flexible Server) · PostgreSQL | MySQL 8.0 EOL; TLS obligatorio |
| Seguridad | Autenticación (Entra ID), CSRF, secretos en Key Vault / Managed Identity | Sin secretos en texto plano |

## Pendientes antes de cerrar la Fase 1

- [ ] Ejecutar OWASP Dependency-Check sobre `WebContent/WEB-INF/lib/` y `mysql-connector/` para confirmar los CVEs.
- [ ] Construir y desplegar la línea base (`ant war` + `docker compose up`) para validar [B-02](blockers.md#b-02) y capturar el comportamiento actual.
- [ ] Crear `.copilot-project.yml` si el flujo del playbook lo requiere.

## Entregables de esta fase

| Archivo | Contenido |
| --- | --- |
| [blockers.md](blockers.md) | Bloqueos categorizados por severidad |
| [dependencies.md](dependencies.md) | Grafo de componentes y librerías (Mermaid) |
| [inventory/spring-config.md](inventory/spring-config.md) | XML de Spring, `web.xml`, `server.xml` de Liberty |
| [inventory/controllers.md](inventory/controllers.md) | Controllers, servlets, filters, JSPs y mapa de URLs |
| [inventory/services-repositories.md](inventory/services-repositories.md) | Services y acceso a datos |
| [inventory/persistence.md](inventory/persistence.md) | iBATIS, SQL maps, modelo de datos y equivalencias |
| [inventory/dependencies-pom.md](inventory/dependencies-pom.md) | Dependencias, CVEs y build Ant |
| [inventory/javax-usages.md](inventory/javax-usages.md) | Mapa del namespace change `javax.*` → `jakarta.*` |
| [features/](features/) | 3 features funcionales |
