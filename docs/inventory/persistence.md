# Inventario: persistencia

> **Sistema analizado:** `legacy/java/jakarta-ee/student-web-app` · **Fase 1 (assessment)** · 2026-10-04

## Resumen

| Aspecto | Valor |
| --- | --- |
| ORM / mapper | **iBATIS SQL Maps 2.3.0** (`ibatis-sqlmap-2.3.0.jar`). No es MyBatis 3 |
| Hibernate (`*.hbm.xml`, `hibernate.cfg.xml`) | No |
| JPA (`@Entity`, `persistence.xml`, `javax.persistence`) | No |
| JdbcTemplate / Spring Data | No |
| Base de datos | MySQL 8.0 (`mysql:8.0` en docker-compose, Connector/J 8.0.33) |
| DataSource | JNDI `jdbc/StudentDB` del servidor Liberty (pool de 2 a 10 conexiones) |
| Archivos de mapeo | 1 (`Student_SqlMap.xml`) |
| Sentencias SQL | 2 (1 SELECT, 1 INSERT) |
| Tablas | 1 (`student_profiles`) |
| Gestión de transacciones | Manual con la API de iBATIS (`type="JDBC"`), sin Spring |
| Migraciones de esquema (Flyway/Liquibase) | No. Solo un script DDL manual |

## Configuración de iBATIS

**Ruta:** [resources/sql-map-config.xml](../../legacy/java/jakarta-ee/student-web-app/resources/sql-map-config.xml) · **Líneas:** 15

| Elemento | Valor | Líneas |
| --- | --- | --- |
| DTD | `-//ibatis.apache.org//DTD SQL Map Config 2.0//EN` | L2-L4 |
| `settings useStatementNamespaces` | `true`: los IDs se invocan con namespace (`com.azure.sample.StudentMapper.listStudent`) | L6 |
| `transactionManager` | `JDBC` | L8 |
| `dataSource` | `JNDI` con `DataSource=jdbc/StudentDB` (nombre JNDI global, no `java:comp/env`) | L9-L11 |
| `sqlMap` | `org/sample/azure/student/msfaa/shared/persistence/xml/Student_SqlMap.xml` | L14 |

- La inicializa `MyBatisUtil` en un bloque `static`, fuera del ciclo de vida de Spring.
- El paquete del mapeo (`msfaa.shared.persistence`) no coincide con el del código (`coreft`). Parece heredado de otro sistema.

## `Student_SqlMap.xml`

**Ruta:** [resources/org/sample/azure/student/msfaa/shared/persistence/xml/Student_SqlMap.xml](../../legacy/java/jakarta-ee/student-web-app/resources/org/sample/azure/student/msfaa/shared/persistence/xml/Student_SqlMap.xml) · **Namespace:** `com.azure.sample.StudentMapper`

| ID | Tipo | SQL | Parámetros | Resultado | Líneas |
| --- | --- | --- | --- | --- | --- |
| `listStudent` | select | `SELECT id, name, email, major FROM student_profiles` | — | `resultClass=StudentProfile` (mapea por nombre de columna) | [L8-L10](../../legacy/java/jakarta-ee/student-web-app/resources/org/sample/azure/student/msfaa/shared/persistence/xml/Student_SqlMap.xml#L8-L10) |
| `addStudent` | insert | `INSERT INTO student_profiles (name, email, major) VALUES (#name#, #email#, #major#)` | `parameterClass=java.util.Map` | — | [L13-L15](../../legacy/java/jakarta-ee/student-web-app/resources/org/sample/azure/student/msfaa/shared/persistence/xml/Student_SqlMap.xml#L13-L15) |

- `#param#` genera un `PreparedStatement` con parámetros: **no hay riesgo de SQL injection**. No se usa la sustitución `$param$`.
- `listStudent` no tiene `ORDER BY` ni paginación: el orden es indefinido y el volumen no tiene límite.
- `addStudent` no devuelve la clave generada (no hay `<selectKey>`).
- Los comentarios del archivo están en chino (`SELECT 操作`, `INSERT 操作`).

## Esquema de base de datos

**Ruta:** [database/create_table.sql](../../legacy/java/jakarta-ee/student-web-app/database/create_table.sql)

| Columna | Tipo | Restricciones |
| --- | --- | --- |
| `id` | `INT AUTO_INCREMENT` | `PRIMARY KEY` |
| `name` | `VARCHAR(255)` | Admite NULL |
| `email` | `VARCHAR(255)` | Admite NULL, **sin UNIQUE** |
| `major` | `VARCHAR(255)` | Admite NULL |

- El script empieza con `DROP TABLE IF EXISTS student_profiles` ([L1](../../legacy/java/jakarta-ee/student-web-app/database/create_table.sql#L1)): **borra todos los datos** si se ejecuta contra una BD en uso.
- Usa sintaxis propia de MySQL (`AUTO_INCREMENT`).
- No hay índices aparte de la PK, ni columnas `NOT NULL`, aunque el formulario marca los tres campos como obligatorios.

## Mapeo objeto-relacional

| Clase | Tabla | Estrategia de ID | Asociaciones |
| --- | --- | --- | --- |
| `StudentProfile` (`int id`, `String name`, `String email`, `String major`) | `student_profiles` | Identity (`AUTO_INCREMENT`) | Ninguna |

## Patrones a marcar (iBATIS 2 hacia un stack moderno)

| Patrón | Riesgo | Acción en la migración |
| --- | --- | --- |
| API `com.ibatis.sqlmap.client.*` (`SqlMapClient`, `SqlMapSession`) | iBATIS se retiró en 2010 (Apache Attic): no recibe parches | Reescribir el acceso a datos (5 puntos) |
| DTD SQL Map 2.0, `resultClass`/`parameterClass`, sintaxis `#param#` | Incompatibles con MyBatis 3 | Reescribir el mapper (2 sentencias) |
| `dataSource type="JNDI"` | Spring Boot embebido no expone JNDI por defecto | Pasar a `spring.datasource.*` |
| Transacciones manuales | Fáciles de romper: el email se envía después del commit y se llama a `endTransaction` tras el commit | `@Transactional` en el service |
| `MyBatisUtil` estático | Impide la inyección de dependencias y los tests | Convertirlo en un bean gestionado por Spring |
| `useSSL=false&allowPublicKeyRetrieval=true` en la URL JDBC | Tráfico sin cifrar. Azure Database for MySQL exige TLS por defecto | Habilitar TLS |

## Equivalencias de referencia (la decisión es de Fase 2)

| Elemento iBATIS 2 | MyBatis 3 (`mybatis-spring-boot-starter`) | Spring Data JPA (Hibernate 6) | `JdbcClient` (Spring 6.1+) |
| --- | --- | --- | --- |
| `sql-map-config.xml` | Propiedades `mybatis.*` en `application.yml` | Propiedades `spring.jpa.*` | — |
| `<sqlMap namespace>` | Interface `@Mapper` + XML con `namespace` | `StudentProfileRepository extends JpaRepository<StudentProfile, Integer>` | Clase `@Repository` |
| `<select id="listStudent" resultClass>` | `<select resultType>` o `@Select` | `findAll()` heredado | `jdbcClient.sql(...).query(StudentProfile.class).list()` |
| `<insert>` con `#name#` | `#{name}` + `useGeneratedKeys` | `save(entity)` + `@GeneratedValue(strategy = IDENTITY)` | `jdbcClient.sql(...).param(...).update(keyHolder)` |
| `StudentProfile` (POJO) | Sin cambios | `@Entity` + `@Table(name = "student_profiles")` con `jakarta.persistence` | Sin cambios |
| Transacción manual | `@Transactional` | `@Transactional` | `@Transactional` |

> **Nota para Fase 2:** el README del lab menciona Hibernate y `HibernateTemplate`, pero **este sistema no usa Hibernate**. Lo que hay que decidir es cómo sustituir iBATIS 2: MyBatis 3, Spring Data JPA o JdbcClient.
