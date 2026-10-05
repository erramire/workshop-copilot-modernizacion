# ADR-003: Cambio de namespace de `javax` a `jakarta` con OpenRewrite

- **Estado:** Aceptado
- **Fecha:** 2026-10-04
- **Decisores:** responsable del taller (decisión 3 del planning, aceptada en el chat)

## Contexto
- 4 de los 9 archivos Java importan paquetes `javax.*` afectados: 24 imports en total (`javax.servlet` ×19, `javax.mail` ×5). `web.xml` también usa el namespace de Java EE 8. Detalle en [inventory/javax-usages.md](../inventory/javax-usages.md).
- Esos 4 archivos son los servlets y el filtro, que se eliminarán más adelante ([ADR-007](ADR-007-capa-web.md)). Aun así, el taller pide OpenRewrite como primer paso y la línea base tiene que compilar en `jakarta.*` antes de empezar a refactorizar.
- No hay código generado (JAXB, JAX-WS ni XMLBeans), así que no hay nada que regenerar.
- Las versiones nuevas de OpenRewrite se distribuyen por el repositorio Code Genome Project, que exige autenticación. Las últimas publicadas en Maven Central, accesibles sin credenciales, son `rewrite-maven-plugin` 6.46.1, `rewrite-migrate-java` 3.42.1 y `rewrite-logging-frameworks` 3.32.0 (consulta del 2026-10-04).
- Las recetas necesitan información de tipos: el código copiado debe compilar con sus APIs originales antes de ejecutarlas.

## Opciones consideradas
1. **OpenRewrite en una sola pasada sobre la copia.**
   - A favor: es una herramienta probada, el resultado es repetible y es lo que pide el taller.
   - En contra: necesita dependencias temporales y versiones fijadas.
2. **Cambio manual.**
   - A favor: con 24 imports es trivial.
   - En contra: no cumple la regla del taller y en proyectos más grandes es fácil dejarse algo.
3. **Capa de compatibilidad (shim) en runtime.**
   - En contra: degrada el rendimiento y no es una solución sostenible.

## Decisión
Opción 1, ejecutada desde la línea de comandos de Maven, sin añadir el plugin al `pom.xml` de forma permanente.

| Elemento | Valor |
| --- | --- |
| Plugin | `org.openrewrite.maven:rewrite-maven-plugin:6.46.1` |
| Recetas | `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta` y `org.openrewrite.java.logging.slf4j.Log4j1ToSlf4j1` |
| Artefactos de las recetas | `org.openrewrite.recipe:rewrite-migrate-java:3.42.1` y `org.openrewrite.recipe:rewrite-logging-frameworks:3.32.0` |
| Precondición | El código copiado compila con dependencias temporales: `javax.servlet-api` 4.0.1, `javax.mail-api` 1.6.2 y `log4j` 1.2.17 (las tres con scope `provided`) e `ibatis-sqlmap` 2.3.0 |
| Cambio manual | `org.codehaus.jackson.map.ObjectMapper` pasa a `com.fasterxml.jackson.databind.ObjectMapper`: 1 import, sin receta verificada |
| Después de la receta | Quitar las dependencias temporales de `javax` y `log4j`, y también las APIs `jakarta` que haya añadido la receta, porque ya las aportan los starters de Spring Boot |
| Validación | `./mvnw compile` en verde y ningún import de `javax.servlet`, `javax.mail`, `org.apache.log4j` ni `org.codehaus.jackson` |

El comando exacto está en el paso 1 de [migration-plan.md](../migration-plan.md).

Quedan fuera de OpenRewrite porque desaparecen:
- `web.xml` y los XML de Spring: los sustituye la autoconfiguración de Spring Boot ([ADR-007](ADR-007-capa-web.md)).
- Las JSP: se reescriben en Thymeleaf ([ADR-008](ADR-008-frontend.md)).
- Los features de Open Liberty: el runtime pasa a Tomcat embebido ([ADR-004](ADR-004-build-empaquetado.md)).

**Plan B:** si las versiones fijadas no resuelven o no son compatibles entre sí, se migran los 24 imports a mano, se anota en `migration/migration-log.md` y se continúa. No es bloqueante.

## Consecuencias
- **Positivas:** queda demostrado el flujo de OpenRewrite que enseña el taller. Además, log4j 1 pasa a SLF4J en el mismo paso.
- **Negativas:** durante el paso 1 hay librerías con CVEs en el classpath de compilación. Las de scope `provided` nunca se empaquetan; iBATIS se elimina en el paso 4.
- **Riesgos a monitorear:** R-02 y R-13 en [risks.md](../risks.md).

## Referencias
- [inventory/javax-usages.md](../inventory/javax-usages.md)
- https://docs.openrewrite.org/recipes/java/migrate/jakarta/javaxmigrationtojakarta
- https://docs.openrewrite.org/recipes/java/logging/slf4j/log4j1toslf4j1
