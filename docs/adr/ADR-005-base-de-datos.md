# ADR-005: Base de datos y migraciones de esquema

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisiones 5 y 12 del planning, aceptadas en el chat)

## Contexto
- El legacy usa MySQL 8.0, que llegó a fin de vida en abril de 2026, con una conexión sin TLS y un DataSource obtenido por JNDI de Liberty.
- El esquema se crea con un script DDL que empieza por `DROP TABLE`, y no hay herramienta de migraciones (DAT-02, DAT-03).
- `infra/main.bicep` no crea ninguna base de datos, y la Container App Java escala de 0 a 3 réplicas.
- El lab .NET usa EF Core InMemory durante el taller.

## Opciones consideradas
1. **H2 en memoria (modo MySQL) por defecto, MySQL opcional mediante variables de entorno y esquema con Flyway.**
   - A favor: no hace falta infraestructura nueva y es equivalente a InMemory en .NET.
   - En contra: los datos son efímeros.
2. **Azure Database for MySQL Flexible Server desde el taller.**
   - A favor: persistencia real.
   - En contra: hay que ampliar el Bicep, cuesta más y tarda más en desplegarse.
3. **MySQL en otro contenedor de Container Apps.**
   - En contra: no tiene almacenamiento persistente gestionado y añade operación sin aportar nada al taller.

## Decisión
Opción 1.

- **URL por defecto** (en `application.yml`): `jdbc:h2:mem:studentdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`.
- **MySQL:** se activa definiendo `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`, con el driver `mysql-connector-j`. La URL tiene que exigir TLS (`sslMode=REQUIRED` o superior), y la contraseña se pasa como secreto ([ADR-010](ADR-010-configuracion-observabilidad.md)).
- **Esquema con Flyway** en `src/main/resources/db/migration/V1__create_student_profiles.sql`:
  - Mismo esquema que el legacy: `id INT AUTO_INCREMENT PRIMARY KEY`, y `name`, `email` y `major` como `VARCHAR(255)` que admiten NULL, sin UNIQUE.
  - **Sin `DROP TABLE`**.
  - El mismo script vale para H2 en modo MySQL y para MySQL.
- `spring.jpa.hibernate.ddl-auto=validate`: Hibernate nunca crea ni modifica tablas.
- **Adoptar una base de datos legacy existente:** arrancar una única vez con `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`. Flyway marca la versión 1 como baseline y no ejecuta V1. El valor por defecto es `false`.
- **En el taller:** los datos se pierden al reiniciar o al escalar a cero, y cada réplica tendría su propia base de datos. Por eso, en Fase 4 la Container App Java debe usar `maxReplicas: 1`.
- **En producción** (fuera de alcance): Azure Database for MySQL Flexible Server 8.4 con TLS y autenticación de Entra ID mediante Managed Identity. Necesita su propio ADR.

## Consecuencias
- **Positivas:**
  - El taller no necesita infraestructura adicional.
  - El esquema queda versionado y desaparece el DDL destructivo.
  - La misma imagen funciona con H2 o con MySQL.
- **Negativas:** H2 en modo MySQL no es MySQL, y los datos del taller son efímeros.
- **Riesgos a monitorear:** R-04 y R-05 en [risks.md](../risks.md).

## Referencias
- [inventory/persistence.md](../inventory/persistence.md) y [blockers.md](../blockers.md) (DAT-02, DAT-03, DAT-04)
