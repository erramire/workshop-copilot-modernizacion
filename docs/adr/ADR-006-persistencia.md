# ADR-006: Persistencia: Spring Data JPA con anotaciones en lugar de iBATIS 2

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisión 4 del planning, aceptada en el chat)

## Contexto
- No hay Hibernate ni `HibernateTemplate`. La persistencia es **iBATIS SQL Maps 2.3.0**, retirado en 2010 (API-02, DAT-01):
  - Un `MyBatisUtil` estático.
  - Un único `Student_SqlMap.xml` con 2 sentencias.
  - Acceso desde 5 puntos del código, 3 de ellos en servlets que se saltan el service.
- Las transacciones son manuales: `startTransaction`, `commitTransaction` y `endTransaction`.
- Spring 6 no tiene integración con iBATIS.
- El modelo es trivial: 1 entidad, 1 tabla, sin relaciones y 2 operaciones (listar todo e insertar).

## Opciones consideradas
1. **Spring Data JPA con entidades anotadas con `@Entity`** (Hibernate ORM 6.6 a través de Spring Boot).
   - A favor: es el estándar, no requiere SQL a mano para 2 operaciones y es lo que espera el lab.
   - En contra: Hibernate es mucho para tan poco.
2. **MyBatis 3 con mappers XML.**
   - A favor: conserva el SQL explícito.
   - En contra: más ficheros para 2 sentencias y un modelo distinto al resto del taller.
3. **`JdbcClient`** (disponible desde Spring 6.1).
   - A favor: ligero.
   - En contra: SQL y mapeo manuales, y se aleja de lo que espera el lab.

## Decisión
Opción 1.

- **`domain.StudentProfile`**:
  - Anotada con `@Entity` y `@Table(name = "student_profiles")`.
  - `Integer id` con `@Id` y `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
  - `name`, `email` y `major` como `String` con `@Column(length = 255)`.
  - Imports de `jakarta.persistence.*`.
- **`infrastructure.persistence.StudentProfileRepository`** extiende `JpaRepository<StudentProfile, Integer>`, sin queries propias.
- **`application.StudentService`** es el único que usa el repositorio:
  - `listStudents()` llama a `findAll(Sort.by("id"))` y es `@Transactional(readOnly = true)`. Ordenar por id hace explícito el orden que el legacy tenía de hecho.
  - `register(...)` llama a `save(...)`, es `@Transactional` y devuelve la entidad guardada con su id.
  - Inyección por constructor.
  - Los errores de base de datos se propagan como excepciones, no como un `boolean`, y es la capa web quien decide el mensaje ([ADR-007](ADR-007-capa-web.md)).
- No hay XML de mapeo ni `persistence.xml`.
- Se eliminan `MyBatisUtil`, `sql-map-config.xml`, `Student_SqlMap.xml` y la dependencia `ibatis-sqlmap`.
- `spring.jpa.open-in-view=false`.

### Equivalencias

| iBATIS 2 | Spring Data JPA |
| --- | --- |
| `SqlMapClient` estático (`MyBatisUtil`) | Bean `StudentProfileRepository` inyectado |
| `listStudent` (`SELECT id, name, email, major FROM student_profiles`) | `findAll(Sort.by("id"))` |
| `addStudent` (`INSERT ... VALUES (#name#, #email#, #major#)`) | `save(entity)`, con el id generado por la base de datos (IDENTITY) |
| `resultClass=StudentProfile` | `@Entity` y `@Column` |
| `startTransaction`, `commitTransaction`, `endTransaction` | `@Transactional` |
| DataSource JNDI `jdbc/StudentDB` | `spring.datasource.*` ([ADR-005](ADR-005-base-de-datos.md)) |

### Hibernate 6: puntos de atención
- No aplica ninguna de las APIs eliminadas que vigila el agente de migración (Criteria legacy, `createSQLQuery`, `UserType`, HQL), porque el sistema no las usaba.
- `ddl-auto=validate` contra el esquema de Flyway detecta al arrancar cualquier diferencia de tipos.

## Consecuencias
- **Positivas:**
  - Se pasa de 5 puntos de acceso a datos a 1.
  - Las transacciones pasan a ser declarativas.
  - Desaparece una librería retirada.
  - Encaja con lo que espera el lab: anotaciones y Spring Data JPA.
- **Negativas:** se añade Hibernate para solo 2 operaciones. Es estándar y lo gestiona Spring Boot.
- **Riesgos a monitorear:** R-04 en [risks.md](../risks.md).

## Referencias
- [inventory/persistence.md](../inventory/persistence.md) y [inventory/services-repositories.md](../inventory/services-repositories.md)
