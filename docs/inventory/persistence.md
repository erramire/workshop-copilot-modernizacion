# Inventario: persistencia

> Sistema: `legacy/java/jakarta-ee/student-web-app/`. Las rutas son relativas a esa carpeta.

## Resumen

| Aspecto | Valor |
| --- | --- |
| ORM / mapper | **iBATIS SqlMaps 2.3.0** (`WebContent/WEB-INF/lib/mybatis/ibatis-sqlmap-2.3.0.jar`). No es MyBatis 3 |
| Hibernate / JPA | **No se usa**: 0 `.hbm.xml`, 0 `@Entity`, 0 `persistence.xml` y 0 imports `javax.persistence` |
| JdbcTemplate / Spring JDBC | No |
| Base de datos | MySQL 8.0 (`docker-compose.yml`) |
| Driver | MySQL Connector/J 8.0.33 (`com.mysql.cj.jdbc.Driver`). Lo provee Liberty como librería compartida; no va en el WAR |
| DataSource | JNDI `jdbc/StudentDB`, definido en `liberty_config/server-docker.xml` (líneas 35-41, pool de 2 a 10 conexiones) |
| Transacciones | `transactionManager type="JDBC"` de iBATIS con llamadas manuales |
| Tablas | 1 (`student_profiles`) |
| Statements SQL | 2 (1 SELECT y 1 INSERT) |
| Entidades / modelos | 1 POJO (`StudentProfile`) |

## Configuración de iBATIS: `resources/sql-map-config.xml`

**Líneas:** 15

- DTD `-//ibatis.apache.org//DTD SQL Map Config 2.0//EN`.
- `useStatementNamespaces="true"`.
- `transactionManager type="JDBC"` con `dataSource type="JNDI"` y `DataSource = jdbc/StudentDB` (líneas 8-12).
  - El lookup usa el nombre global de Liberty (`jdbc/StudentDB`), no el `resource-ref` de `web.xml` (`java:comp/env/jdbc/StudentDB`).
- 1 `sqlMap`: `org/sample/azure/student/msfaa/shared/persistence/xml/Student_SqlMap.xml`. El paquete `msfaa.shared` no coincide con el del código (`coreft`); parece un vestigio de otro proyecto.
- La carga la hace `MyBatisUtil` en un bloque estático (`MyBatisUtil.java:16-17`), fuera de Spring.

## Mapeos: `Student_SqlMap.xml`

**Namespace:** `com.azure.sample.StudentMapper` · **Líneas:** 17

| Statement | Tipo | SQL | Parámetro | Resultado | Usado por |
| --- | --- | --- | --- | --- | --- |
| `listStudent` | select (líneas 8-10) | `SELECT id, name, email, major FROM student_profiles` | — | `resultClass=org.sample.azure.student.coreft.StudentProfile` (auto-mapping por nombre) | StudentService, IndexServlet, StudentProfileListServlet |
| `addStudent` | insert (líneas 13-15) | `INSERT INTO student_profiles (name, email, major) VALUES (#name#, #email#, #major#)` | `parameterClass=java.util.Map` | — (no recupera el id generado) | StudentService, AddStudentServlet |

### Observaciones
- Los parámetros usan `#param#`, que genera un `PreparedStatement`: **no hay riesgo de SQL injection**. No se usa la sustitución literal `$param$`.
- `listStudent` no tiene `ORDER BY` ni límite: el orden no es determinista y no hay paginación.
- Los comentarios del XML están en chino (`SELECT 操作`, `INSERT 操作`), otro vestigio.

## Modelo de datos

### Tabla `student_profiles` (`database/create_table.sql`)

| Columna | Tipo | Restricciones |
| --- | --- | --- |
| `id` | `INT AUTO_INCREMENT` | PK |
| `name` | `VARCHAR(255)` | Ninguna (acepta NULL) |
| `email` | `VARCHAR(255)` | Ninguna (acepta NULL, sin UNIQUE) |
| `major` | `VARCHAR(255)` | Ninguna (acepta NULL) |

- El script empieza con `DROP TABLE IF EXISTS`: borra los datos si se vuelve a ejecutar.
- No tiene índices adicionales, auditoría ni soft-delete.
- No hay herramienta de migración de esquema (Flyway / Liquibase).

### POJO `StudentProfile` (`src/org/sample/azure/student/coreft/StudentProfile.java`)
- Campos: `int id`, `String name`, `String email`, `String major`, con getters y setters.
- Implementa `Serializable` y no tiene anotaciones (no es `@Entity`).

## Equivalencias iBATIS 2 → MyBatis 3 (por si Fase 2 mantiene el mapper XML)

| iBATIS 2 | MyBatis 3 |
| --- | --- |
| `<sqlMapConfig>` | `<configuration>` o propiedades `mybatis.*` |
| `<sqlMap namespace="...">` | `<mapper namespace="...">`, idealmente con el FQCN de una interfaz `@Mapper` |
| `resultClass` | `resultType` |
| `parameterClass` | `parameterType` |
| `#name#` | `#{name}` |
| `$name$` | `${name}` |
| `SqlMapClient` / `SqlMapSession` | `SqlSession` o interfaces `@Mapper` inyectadas |
| `startTransaction` / `commitTransaction` / `endTransaction` | `@Transactional` de Spring |
| `dataSource type="JNDI"` | DataSource gestionado por Spring (`spring.datasource.*`) |

## Alternativas para evaluar en Fase 2 (aquí no se decide)

| Opción | Implicación |
| --- | --- |
| MyBatis 3 + `mybatis-spring-boot-starter` | Conserva SQL explícito. La conversión de los 2 statements es mecánica |
| Spring JDBC (`JdbcClient` / `JdbcTemplate`) | Elimina el mapper XML. Son 2 queries triviales |
| Spring Data JPA | Requiere convertir `StudentProfile` en `@Entity`. El Lab 02 sugiere esta vía, pero hoy no hay JPA |

## Patrones de Hibernate a vigilar (checklist)

| Patrón | ¿Presente? |
| --- | --- |
| `org.hibernate.Criteria` (legacy) | No |
| `Session.createSQLQuery()` | No |
| `UserType` custom | No |
| `Filter` de Hibernate | No |
| Mappings `.hbm.xml` | No |

**Los riesgos de upgrade a Hibernate 6 no aplican a este sistema.** El riesgo de persistencia está en reemplazar iBATIS 2, una librería retirada, y en sacar el DataSource del JNDI del servidor.
