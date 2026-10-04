# Resumen del assessment Spring legacy: student-web-app

> **Fase 1 (assessment)** · 2026-10-04 · Siguiente fase: `@spring-legacy-planning`

## Alcance

- **Sistema analizado:** `legacy/java/jakarta-ee/student-web-app`, el proyecto objetivo del [Lab 02](../labs/lab-02-java/README.md).
- `legacy/java/` es un clon completo de `Azure-Samples/java-migration-copilot-samples`. El resto de proyectos queda fuera de alcance:

| Proyecto | Stack | Motivo |
| --- | --- | --- |
| `asset-manager` | Spring Boot (parent), Java 8, Maven multi-módulo | Ya es Spring Boot; pertenece a otro workshop |
| `rabbitmq-sender` | Spring Boot, Java 17 | Ya es Spring Boot |
| `todo-web-api-use-oracle-db` | Spring Boot, Java 17 | Ya es Spring Boot |
| `mi-sql-public-demo` | Java 17, Maven | No es Spring legacy |
| `ContosoUniversity`, `Malshinon` | .NET | No le corresponden a este agente |

- **Input que falta (no bloquea):** no existe `.copilot-project.yml`. El contexto se tomó de `.github/copilot-instructions.md` y del README del lab.
- **Limitación:** no había terminal disponible. No se compiló ni ejecutó la app, no se pasó ningún escáner de CVEs y las líneas de código se contaron a mano (aproximadas).

## Stack detectado

| Aspecto | Valor |
| --- | --- |
| Build | Apache Ant, con JARs vendorizados y packaging WAR |
| Java | 11 (source/target en `build.xml`). El runtime de la imagen es Java 17 (OpenJ9) |
| Spring | **5.3.23**: Spring MVC con configuración XML más anotaciones. La documentación dice 5.3.39 |
| Struts | No se usa |
| Persistencia | **iBATIS SQL Maps 2.3.0** con mapeos XML y DataSource por JNDI. No hay Hibernate ni JPA |
| Frontend | JSP 2.3 con scriptlets (4 vistas), sin JSTL |
| Otros frameworks | 3 servlets clásicos (Servlet 4.0) en paralelo a Spring MVC, JavaMail 1.6 por JNDI, Jackson 1.9.13 y log4j 1.2.17 |
| Servidor | Open Liberty 25.0.0.7 (Java EE 8 Web Profile) |
| Base de datos | MySQL 8.0 con Connector/J 8.0.33 |

## Métricas

| Métrica | Valor |
| --- | --- |
| LOC Java | ≈ 680 (9 archivos) |
| LOC JSP | ≈ 445 (4 archivos) |
| XML de configuración | 6 archivos, 170 líneas |
| Controllers Spring MVC | 2 (4 handlers) |
| Servlets clásicos | 3 (4 handlers) |
| Filtros | 1 (sin registrar) |
| Services | 1 |
| Repositories / DAOs | 0 (iBATIS se usa directamente desde 5 puntos) |
| Entidades de persistencia | 1 (`StudentProfile`, tabla `student_profiles`) |
| Sentencias SQL | 2 |
| Features de negocio | 3 |
| Tests | **0** |
| Archivos afectados por `javax` → `jakarta` | 4 de 9 (24 imports), más `web.xml` |
| Ratio anotaciones / XML (beans) | 60% / 40% (75% / 25% contando solo los activos) |

## Señales de alarma

- **Spring 5.3.x** lleva sin soporte OSS desde agosto de 2024, y los fixes posteriores solo se publican con soporte comercial.
- **iBATIS 2.3.0** se retiró en 2010. Es el equivalente al "Hibernate 3.x" del checklist: un cambio grande en la persistencia.
- **log4j 1.2.17** es EOL desde 2015 y tiene CVEs críticas.
- **No hay ningún test.**
- No aplican: Java 6/7, Spring 3.0.x, Hibernate 3.x ni Struts.

## CVEs críticas detectadas

| Componente | CVE | Severidad | ¿Explotable hoy? |
| --- | --- | --- | --- |
| log4j 1.2.17 | CVE-2019-17571, CVE-2022-23305 | Crítica 9.8 | No: esas clases y appenders no se usan |
| spring-web 5.3.23 | CVE-2016-1000027 | Crítica 9.8 (disputada) | No: no se usa HttpInvoker |
| spring-webmvc 5.3.23 | CVE-2023-20860 | Crítica (según spring.io) | No: no hay Spring Security |

Además hay CVEs de severidad alta en spring-web (CVE-2024-22243/22259/22262), Connector/J (CVE-2023-22102), Jackson 1.x (CVE-2019-10172) y log4j (CVE-2022-23302, CVE-2022-23307, CVE-2021-4104). El detalle está en [inventory/dependencies-pom.md](inventory/dependencies-pom.md).

**Conclusión:** con el uso actual del código ninguna es explotable, pero todas bloquean una revisión de compliance y no tienen parche OSS en las versiones instaladas, así que el upgrade es obligatorio.

## Los 5 bloqueos principales

1. **Secretos hardcoded** en `Dockerfile`, `server-docker.xml`, `server-docker.env` y `docker-compose.yml` ([SEC-01](blockers.md#4-seguridad)).
2. **iBATIS 2.3.0 está retirado:** hay que reescribir el acceso a datos (5 puntos y el mapper), porque no tiene integración con Spring 6 ([API-02](blockers.md#2-apis-deprecated-o-removidas)).
3. **Spring 5.3 sobre `javax.*`:** hay que pasar a Spring 6, `jakarta.*` y Java 17+ ([PLT-01](blockers.md#1-plataforma-build-y-empaquetado)).
4. **Las JSP no funcionan en el JAR ejecutable** que exige el workshop: hay que migrar las 4 vistas o mantener WAR ([PLT-02](blockers.md#1-plataforma-build-y-empaquetado)).
5. **Sin tests y con flujos duplicados que se comportan distinto:** no hay forma de demostrar que la app migrada es equivalente ([QA-01](blockers.md#6-calidad-y-pruebas), [FUN-01](blockers.md#5-funcional-y-arquitectura)).

Lista completa: [blockers.md](blockers.md) (4 críticos, 11 altos, 11 medios y 10 bajos).

## Recomendación para Fase 2

- **Target:** Spring Boot 3.x con Java 21 (Temurin) y JAR ejecutable, como establece `.github/copilot-instructions.md`. Quarkus incumpliría esa restricción y no aporta ventajas con este tamaño, así que no se recomienda evaluarlo salvo que lo pida el cliente.
- **Upgrade in-place o greenfield:** el código es muy pequeño (≈ 680 LOC) y gran parte hay que reescribirla de todos modos (persistencia, vistas, servlets). Las dos opciones son viables; hay que validarlo con el cliente.
- **Decisiones que Fase 2 tiene que tomar con el cliente:**
  1. Persistencia: sustituir iBATIS 2 por MyBatis 3, Spring Data JPA o JdbcClient (el lab asume JPA).
  2. Vistas: pasar las JSP a Thymeleaf (con JAR) o mantener JSP (con WAR).
  3. Consolidación: qué URLs se mantienen (`/`, `/app/*`, `/addStudent`, `/studentProfileList`) y qué redirects de compatibilidad hacen falta.
  4. Comportamiento correcto del alta: si se envía email en todas las altas, qué validaciones aplican y qué pasa con los emails duplicados.
  5. Correo en Azure: proveedor (Azure Communication Services Email, SendGrid o SMTP) y si el envío debe ser síncrono o asíncrono.
  6. Base de datos destino (p. ej. Azure Database for MySQL Flexible Server con TLS) y herramienta de migraciones de esquema.
  7. Seguridad: autenticación, CSRF y gestión de secretos (Key Vault con Managed Identity).
  8. Observabilidad: logs a stdout y Application Insights.

## Discrepancias detectadas (para el facilitador)

| Fuente | Qué dice | Qué hay en el código |
| --- | --- | --- |
| README del sample, `doc/architecture.md` y JSP | Spring 5.3.39 | Los JARs son 5.3.23 |
| `doc/architecture.md` | "JSON API: REST endpoints" y "CRUD operations" | No hay endpoints REST ni JSON; solo alta y consulta |
| Nombre de `MyBatisUtil` y la documentación | MyBatis | Es iBATIS 2.3.0 |
| `labs/lab-02-java/README.md` | PetClinic, Hibernate y `HibernateTemplate`, JUnit 4, `mvc-core-config.xml`, `spring-config.xml`, CVEs del `pom.xml`, `features/01-gestion-mascotas.md` | Nada de eso existe en student-web-app |
| Lab, paso 4 | "Upgrade in-place (el proyecto tiene tests...)" | Hay 0 tests |
| Lab, pasos 6 y 8 | `cd legacy/java && ./mvnw ...` y `legacy/java/Dockerfile` | El proyecto está en `legacy/java/jakarta-ee/student-web-app` y no tiene `mvnw` |
| Lab, entregables | `cve-report.md`, `maven-dependencies.md`, `spring-xml-config.md`, `dependency-graph.md`, `SUMMARY.md` | Este agente genera los nombres que espera `@spring-legacy-planning`: `dependencies-pom.md` (con las CVEs), `spring-config.md`, `dependencies.md` y este resumen. `javax-usages.md` sí se genera |
| `.github/copilot-instructions.md` | `legacy/` es de solo lectura | Los pasos 5 y 7 del lab modifican archivos y hacen commit dentro de `legacy/java` |

## Entregables

| Archivo | Contenido |
| --- | --- |
| [features/01-consulta-estudiantes.md](features/01-consulta-estudiantes.md) | Feature: consulta de perfiles |
| [features/02-registro-estudiante.md](features/02-registro-estudiante.md) | Feature: alta de perfil |
| [features/03-notificacion-bienvenida.md](features/03-notificacion-bienvenida.md) | Feature: email de bienvenida |
| [dependencies.md](dependencies.md) | Grafos Mermaid de componentes y librerías |
| [inventory/spring-config.md](inventory/spring-config.md) | XML frente a anotaciones, contextos y beans |
| [inventory/controllers.md](inventory/controllers.md) | Controllers, servlets, filtros, JSP y mapa de URLs |
| [inventory/services-repositories.md](inventory/services-repositories.md) | Service, acceso a datos y patrones problemáticos |
| [inventory/persistence.md](inventory/persistence.md) | iBATIS, esquema y equivalencias de referencia |
| [inventory/dependencies-pom.md](inventory/dependencies-pom.md) | Dependencias (sin pom), CVEs y build con Ant |
| [inventory/javax-usages.md](inventory/javax-usages.md) | Mapa del cambio de namespace `javax` → `jakarta` |
| [blockers.md](blockers.md) | 36 bloqueos clasificados |

## Criterios de "Done"

- [x] Stack completo detectado y documentado (Spring, Java, persistencia, frontend)
- [x] Controllers, servlets, services y repositories catalogados
- [x] CVEs identificadas (lista no exhaustiva; falta el escaneo automático cuando exista el `pom.xml`)
- [x] Archivos afectados por el cambio de namespace a `jakarta` contados
- [x] Mapeos XML inventariados con equivalencias (iBATIS, ya que no hay Hibernate)
- [x] `docs/features/` con las features funcionales
- [x] `docs/blockers.md` con los bloqueos clasificados

**Siguiente paso:** `@spring-legacy-planning Revisa el assessment en docs/ y planifica la migración a Spring Boot 3`
