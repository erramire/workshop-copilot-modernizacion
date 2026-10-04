# ADR-005: Persistencia con Spring Data JPA (Hibernate 6.6) y anotaciones

| Campo | Valor |
| --- | --- |
| Estado | Aceptado |
| Fecha | 2026-10-04 |
| Relacionado | [B-08](../blockers.md#b-08), [B-09](../blockers.md#b-09), [B-11](../blockers.md#b-11), [B-17](../blockers.md#b-17), [persistence.md](../inventory/persistence.md), [riesgos](../risks.md) (R-04, R-11, R-12) |

## Contexto

- La persistencia usa iBATIS 2.3.0 (EOL desde 2010) a través del utilitario estático `MyBatisUtil`, con el DataSource JNDI `jdbc/StudentDB`. Son 2 statements (`listStudent` y `addStudent`) en `Student_SqlMap.xml`, sobre 1 tabla.
- No hay Hibernate en el legacy, así que no aplican los breaking changes de Hibernate 3/4/5 → 6 (Criteria legacy, `createSQLQuery`, `UserType`, `.hbm.xml`): hay 0 ocurrencias.
- Spring Boot embebido no expone JNDI.
- El Bicep del taller no crea base de datos. Por comparación, la app .NET usa EF InMemory en el taller.
- MySQL 8.0 está en EOL desde abril de 2026.

## Decisión

- **Spring Data JPA** (`spring-boot-starter-data-jpa`), con Hibernate ORM 6.6 gestionado por el BOM. Solo anotaciones `jakarta.persistence`, sin mapeos en XML.
- **Entidad `StudentProfile`:** `@Entity` y `@Table(name = "student_profiles")`, con `@Id @GeneratedValue(strategy = IDENTITY) Integer id` y 3 columnas `@Column(length = 255)`, nullable como en el DDL legacy. `toString()` no incluye el email.
- **Repositorio:** `StudentProfileRepository extends JpaRepository<StudentProfile, Integer>`. El listado usa `findAll(Sort.by("id"))`.
- **Se eliminan** `MyBatisUtil`, `sql-map-config.xml`, `Student_SqlMap.xml` y la dependencia `org.apache.ibatis:ibatis-sqlmap`.
- **Transacciones declarativas:** `@Transactional` en `StudentService` (`readOnly = true` en las lecturas) y `spring.jpa.open-in-view=false`.
- **Esquema con Flyway:**
  - `V1__create_student_profiles.sql` es el DDL legacy sin `DROP TABLE`.
  - `spring.jpa.hibernate.ddl-auto=validate`.
  - `spring.flyway.baseline-on-migrate=true` y `baseline-version=1`, para adoptar una base existente sin volver a crear la tabla.
- **Motor por entorno:**

| Entorno | Motor | Configuración |
| --- | --- | --- |
| Local, taller y ACA del taller | H2 en memoria con `MODE=MySQL` (default) | Sin variables |
| Entornos reales | Azure Database for MySQL Flexible Server **8.4 LTS** | `SPRING_DATASOURCE_URL` con `sslMode=VERIFY_IDENTITY`; credenciales como secreto o autenticación passwordless con Entra ID |
| Tests de repositorio | MySQL 8.4 con Testcontainers | [ADR-008](ADR-008-test-strategy.md) |

- **Dependencias:** `com.h2database:h2` y `com.mysql:mysql-connector-j` en scope `runtime`, más `flyway-core` y `flyway-mysql`.
- La consola de H2 queda siempre deshabilitada.

## Alternativas consideradas

| Opción | Pros | Contras | Resultado |
| --- | --- | --- | --- |
| MyBatis 3 + `mybatis-spring-boot-starter` | Traducción casi 1:1 del XML | Mantiene el SQL en XML, y el plan del taller pide anotaciones | Descartada |
| **Spring Data JPA** | Mínimo código, transacciones declarativas, alineado con el lab | Cambia el paradigma a ORM | **Elegida** |
| `JdbcClient` de Spring | Simple, con SQL explícito | Más código manual que un repositorio | Descartada |
| MySQL en el Bicep del taller | Igual que producción | Más recursos, costo y tiempo | Descartada para el taller |

## Consecuencias

- **Positivas:**
  - Sin XML ni estado estático; el repositorio se inyecta y se puede probar.
  - Los errores de base de datos dejan de tragarse en silencio y se vuelven visibles.
- **Negativas:**
  - En el taller los datos son efímeros y distintos en cada réplica (R-04). Requiere `maxReplicas: 1` en la Fase 4.
  - H2 y MySQL se comportan distinto en algunos casos. Se mitiga probando el repositorio contra MySQL 8.4 (R-11).
  - Cambia el comportamiento: el listado se ordena por id (antes el orden no estaba definido).

## Validación

- La app arranca con `ddl-auto=validate` sin errores, tanto en H2 como en MySQL 8.4.
- `./mvnw dependency:tree` no incluye `org.apache.ibatis`.
- El test de repositorio pasa contra MySQL 8.4 (Testcontainers).
