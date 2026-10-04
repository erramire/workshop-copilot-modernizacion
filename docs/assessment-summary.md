# Resumen del assessment Spring legacy: student-web-app

> Fase 1 (Assessment) · Agente `@spring-legacy-assessment` · 2026-10-04
> **Alcance:** `legacy/java/jakarta-ee/student-web-app/`, el proyecto objetivo del Lab 02 según `.github/copilot-instructions.md`. Las demás carpetas de `legacy/java/` (`asset-manager`, `mi-sql-public-demo`, `rabbitmq-sender`, `todo-web-api-use-oracle-db`, `Malshinon`, `ContosoUniversity`) son otras muestras del repositorio clonado y quedan fuera de alcance.

## Inventario inicial

- Build tool: **Ant** (`build.xml`); no hay `pom.xml`
- Java version: source/target **11** en la compilación; **Java 17** (OpenJ9) en runtime
- Spring version: **5.3.23** (jars empaquetados; la documentación dice 5.3.39)
- Struts version: **no usa**
- Hibernate version: **no usa**
- Servidor target actual: **Open Liberty 25.0.0.7** (features Java EE 8)
- ORM principal: **iBATIS SqlMaps 2.3.0** con mapper XML y DataSource JNDI
- Frontend: **JSP 2.3 con scriptlets** (sin JSTL)

## Stack detectado

- Web: híbrido entre **3 servlets Java EE y 2 controllers Spring MVC**, con funcionalidad duplicada.
- Base de datos: MySQL 8.0 con Connector/J 8.0.33, que provee el servidor.
- Otras librerías: JavaMail 1.6 (por JNDI), Jackson 1.9.13 (codehaus) y Log4j 1.2.17.
- No usa Spring Boot, Spring Security, Struts, Hibernate/JPA ni tests.

## Métricas

| Métrica | Valor |
| --- | --- |
| LOC Java | ~680 líneas físicas en 9 archivos |
| LOC JSP | 445 en 4 archivos |
| XML de configuración | 6 archivos (170 líneas): `web.xml`, 3 de Spring y 2 de iBATIS. Además, `server-docker.xml` de Liberty |
| Controllers Spring MVC | 2, con 4 endpoints |
| Servlets | 3, con 4 endpoints |
| Filtros | 1 (no registrado) |
| Services | 1 |
| Repositories / DAOs | 0 (hay 1 utilitario estático de iBATIS) |
| Entidades / tablas | 1 POJO y 1 tabla |
| Statements SQL | 2 |
| Beans XML / componentes anotados | 2 beans XML (1 activo) / 3 componentes |
| Archivos afectados por `javax.*` → `jakarta.*` | 4 Java (24 imports) y `web.xml` |
| Tests existentes | **0** |

## Features detectadas

| # | Feature | Rutas | Riesgo |
| --- | --- | --- | --- |
| 01 | [Consulta de estudiantes](features/01-consulta-estudiantes.md) | `/`, `/studentProfileList`, `/app/`, `/app/students` | Bajo |
| 02 | [Alta de estudiante](features/02-alta-estudiante.md) | `/addStudent`, `/app/add-student` | Medio |
| 03 | [Notificación de bienvenida](features/03-notificacion-bienvenida.md) | Se dispara solo desde `POST /addStudent` | Medio |

## CVEs críticas y altas detectadas

| Dependencia | CVE | Severidad | ¿Aplica al código actual? |
| --- | --- | --- | --- |
| log4j 1.2.17 | CVE-2019-17571, CVE-2022-23305 | Crítica 9.8 | No directamente. EOL y sin parche |
| spring-web 5.3.23 | CVE-2016-1000027 | Crítica 9.8 (NVD) | No: no usa HttpInvoker |
| Spring 5.3.23 | CVE-2023-20860 | Crítica (Spring) | No: no hay Spring Security |
| mysql-connector-j 8.0.33 | CVE-2023-22102 | Alta 8.3 | La versión está afectada |
| jackson-mapper-asl 1.9.13 | CVE-2019-10172 | Alta 7.5 | Bajo |

Ninguna se puede explotar de forma directa con el código actual, pero **4 de las 5 librerías de runtime ya no tienen soporte** y no recibirán parches. Hay 21 CVEs en total y el detalle está en [inventory/cve-report.md](inventory/cve-report.md).

## Bloqueos top-5

1. **JSP incompatibles con un JAR ejecutable (B1).** Las 4 vistas usan scriptlets, y la regla del workshop exige un JAR. Hay que elegir entre reescribir las vistas o mantener un WAR.
2. **Recursos JNDI del servidor (B2).** El DataSource y la sesión de correo vienen de Liberty, y en Spring Boot y Container Apps no hay JNDI.
3. **Credenciales en texto plano (B3).** Están en `Dockerfile`, `docker-compose.yml`, `server-docker.env` y `server-docker.xml`, y la conexión JDBC va sin TLS.
4. **Build Ant sin gestión de dependencias (B4).** OpenRewrite necesita Maven, así que la conversión Ant → Maven tiene que ir antes del recipe `javax-to-jakarta`. Además, faltan jars transitivos de Spring.
5. **iBATIS 2.3.0 retirado (B5).** No es MyBatis, y Spring no tiene integración con él desde la versión 4.0. El volumen a reemplazar es bajo (2 statements).

La lista completa de 12 bloqueos está en [blockers.md](blockers.md).

## Lo que no es problema en este sistema

- No hay Hibernate, así que los riesgos de Hibernate 6 no aplican. Tampoco hay Struts ni Acegi.
- No se usan JAXB, CORBA, `sun.misc` ni `setAccessible`: no hay bloqueos para Java 21 en el código propio.
- El cambio de namespace es pequeño (4 archivos) y mecánico.
- La configuración XML de Spring es mínima (1 bean activo). El 75 % de los componentes ya usa anotaciones.
- El SQL está parametrizado (sin SQL injection) y los listados escapan HTML.

## Recomendación para Fase 2

Decisiones para validar con el cliente o el facilitador:

1. **Target.** Las instrucciones del workshop piden Spring Boot 3.x en JAR sobre Container Apps. El título del Lab 02 y el handoff del agente hablan de Jakarta EE 10 + Spring Framework 6.2 (WAR en Liberty). Spring Boot 3 + Java 21 es la continuidad natural, porque la app ya es Spring y es pequeña. Quarkus no tiene una ventaja clara en este caso.
2. **Vistas (B1):** Thymeleaf o WAR con JSP.
3. **Persistencia (B5):** MyBatis 3, Spring JDBC o Spring Data JPA. Las respuestas que el Lab 02 propone para Fase 2 (Hibernate, `HibernateTemplate`, Struts) **no aplican** a este código.
4. **Rutas y comportamiento (B7):** qué rutas legacy se conservan y si el correo de bienvenida existe en el target.
5. **Orden de los pasos (B4):** Ant → Maven antes de OpenRewrite.
6. **Correo y secretos en Azure (B2, B3):** qué servicio de correo usar y cómo inyectar los secretos.
7. **Tests de caracterización (B9)** antes de tocar el código, a partir de los escenarios de paridad de cada feature.

## Entregables de esta fase

| Entregable | Archivo |
| --- | --- |
| Features | [features/](features/) (3 archivos) |
| Grafo de dependencias | [dependencies.md](dependencies.md) |
| Configuración Spring | [inventory/spring-config.md](inventory/spring-config.md) |
| Controllers, servlets y vistas | [inventory/controllers.md](inventory/controllers.md) |
| Services y repositories | [inventory/services-repositories.md](inventory/services-repositories.md) |
| Persistencia | [inventory/persistence.md](inventory/persistence.md) |
| Dependencias (Ant / jars) | [inventory/dependencies-pom.md](inventory/dependencies-pom.md) |
| Usos de `javax.*` (entregable del Lab 02) | [inventory/javax-usages.md](inventory/javax-usages.md) |
| Reporte de CVEs (entregable del Lab 02) | [inventory/cve-report.md](inventory/cve-report.md) |
| Bloqueos | [blockers.md](blockers.md) |

## Insumos faltantes y limitaciones

- **No existe `.copilot-project.yml`**, que es un insumo requerido por esta fase y por `@spring-legacy-planning`. Como contexto se usó `.github/copilot-instructions.md`. El contenido esperado incluye `legacy_tech: java` y `legacy_lang: spring-legacy`.
- **No se ejecutaron comandos ni escaneo automatizado de CVEs.** Las métricas y versiones salen de la lectura directa de archivos y jars, y las LOC de Java son aproximadas.
- **No se validó el arranque en runtime** de la app legacy, incluido el riesgo de los jars transitivos faltantes.
