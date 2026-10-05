# ADR-001: Stack target: Spring Boot 3.5 con Java 21 (Temurin)

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisión 1 del planning, aceptada en el chat)

## Contexto
- El sistema legacy usa Spring Framework 5.3.23 sobre Java 11 y Open Liberty. Spring 5.3 no tiene soporte OSS desde el 31 de agosto de 2024.
- Las reglas del taller (`.github/copilot-instructions.md`) fijan Spring Boot 3.x, Eclipse Temurin 21, JAR ejecutable y el namespace `jakarta.*`.
- A fecha de este ADR ninguna línea 3.x tiene soporte OSS. Spring Boot 3.5, la última 3.x (versión final 3.5.16), lo perdió el 30 de junio de 2026 y solo conserva soporte comercial hasta 2032. La línea OSS vigente es la 4.1, con soporte hasta el 31 de julio de 2027.

## Opciones consideradas
1. **Spring Boot 3.5.x con Java 21.**
   - A favor: cumple las reglas del taller, encaja con el lab y con los agentes, y trae Spring Framework 6.2 sobre Jakarta EE 10.
   - En contra: ya no recibe parches gratuitos.
2. **Spring Boot 4.1.x con Java 21.**
   - A favor: tiene soporte OSS.
   - En contra: incumple la regla "Spring Boot 3.x", añade más cambios (Spring Framework 7, Jakarta EE 11, Hibernate 7, Jackson 3) y el material del taller está escrito para 3.x.
3. **Quarkus.**
   - En contra: obliga a reescribir anotaciones y configuración, incumple la regla del taller y no aporta nada con este tamaño de aplicación.

## Decisión
Opción 1: **Spring Boot 3.5.16** (`spring-boot-starter-parent`) con **Java 21 (Eclipse Temurin)**.

Las versiones de las librerías las fija el BOM de Spring Boot: Spring Framework 6.2.x, Hibernate ORM 6.6.x, Spring Security 6.5.x y Thymeleaf 3.1.x, entre otras.

## Consecuencias
- **Positivas:** alineado con el taller, el lab y los agentes. El salto desde Spring 5.3 a 6.2 es el mínimo posible.
- **Negativas:** la aplicación nace sobre una línea sin parches OSS.
- **Riesgos a monitorear:** R-01 en [risks.md](../risks.md). Si este plan se reutiliza para un cliente real, hay que abrir un ADR para subir a Spring Boot 4.1 antes de producción. Desde 3.5 el salto es incremental, porque el código ya estará en `jakarta.*`.

## Referencias
- [assessment-summary.md](../assessment-summary.md) y [inventory/dependencies-pom.md](../inventory/dependencies-pom.md)
- Calendarios de soporte consultados el 2026-10-04: https://endoflife.date/spring-boot y https://endoflife.date/spring-framework
