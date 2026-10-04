# Inventario: configuración Spring

> **Sistema analizado:** `legacy/java/jakarta-ee/student-web-app` · **Fase 1 (assessment)** · 2026-10-04
> Las rutas son relativas a la carpeta del sistema salvo que se indique otra cosa.

## Resumen

| Métrica | Valor |
| --- | --- |
| Spring Framework (JARs en `WebContent/WEB-INF/lib/spring`) | **5.3.23**. La documentación del sample y las JSP dicen 5.3.39 (ver discrepancias en `assessment-summary.md`) |
| Descriptor de despliegue | `web.xml` Servlet 4.0 (namespace Java EE 8 `xmlns.jcp.org`) |
| Archivos XML de Spring | 3 |
| Contextos Spring | 2: raíz (`ContextLoaderListener`) e hijo (`DispatcherServlet` "spring") |
| `<bean>` declarados en XML | 2 (1 activo, 1 huérfano) |
| `<context:component-scan>` | 3 (uno apunta a un paquete inexistente) |
| Clases `@Configuration` | 0 |
| Componentes con anotaciones | 3 (`@Controller` ×2, `@Service` ×1) |
| Profiles / property placeholders | 0 / 0 |
| Transacciones declarativas (`<tx:*>`, `@Transactional`) | No |
| AOP (`<aop:*>`, `@Aspect`) | No |

## Arranque: `web.xml`

**Ruta:** [WebContent/WEB-INF/web.xml](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml) · **Líneas:** 78

| Elemento | Valor | Líneas |
| --- | --- | --- |
| `context-param contextConfigLocation` | `/WEB-INF/applicationContext.xml` | [L9-L12](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml#L9-L12) |
| `listener` | `org.springframework.web.context.ContextLoaderListener` | [L15-L17](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml#L15-L17) |
| `servlet` "spring" | `DispatcherServlet`, config `/WEB-INF/spring-servlet.xml`, `load-on-startup=1` | [L20-L28](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml#L20-L28) |
| `servlet-mapping` "spring" | `/app/*` | [L30-L33](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml#L30-L33) |
| Servlets fuera de Spring | `IndexServlet` (`/`), `AddStudentServlet` (`/addStudent`), `StudentProfileListServlet` (`/studentProfileList`) | [L36-L62](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml#L36-L62) |
| `resource-ref` | `jdbc/StudentDB` (`javax.sql.DataSource`), `mail/StudentMailSession` (`javax.mail.Session`) | [L64-L76](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/web.xml#L64-L76) |
| Filtros | Ninguno. `CommonHttpServletFilter` existe pero no está registrado | — |

Hallazgos:
- `IndexServlet` está mapeado a `/`, así que reemplaza al *default servlet* del contenedor: cualquier URL sin mapeo (incluidos recursos estáticos) acaba en `IndexServlet`.
- La arquitectura es híbrida: Spring MVC solo atiende `/app/*`. El resto lo atienden servlets clásicos que viven fuera del contenedor de Spring y no pueden recibir inyección de dependencias.

## `applicationContext.xml` (contexto raíz)

**Ruta:** [WebContent/WEB-INF/applicationContext.xml](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/applicationContext.xml) · **Líneas:** 16 · **Beans declarados:** 0

- `<context:component-scan base-package="org.sample.azure.student.coreft.service"/>` ([L11](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/applicationContext.xml#L11)): detecta `StudentService`.
- `<context:component-scan base-package="org.sample.azure.student.coreft.dao"/>` ([L12](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/applicationContext.xml#L12)): **el paquete no existe**, así que el escaneo no encuentra nada.
- No declara DataSource, transaction manager ni property placeholder. La persistencia vive fuera de Spring (ver [persistence.md](persistence.md)).

## `spring-servlet.xml` (contexto del DispatcherServlet)

**Ruta:** [WebContent/WEB-INF/spring-servlet.xml](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/spring-servlet.xml) · **Líneas:** 28 · **Beans declarados:** 1

- `<context:component-scan base-package="org.sample.azure.student.coreft.controller"/>` ([L14](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/spring-servlet.xml#L14)): detecta `StudentController` y `AddStudentController`.
- `<mvc:annotation-driven/>` ([L17](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/spring-servlet.xml#L17)).
- `InternalResourceViewResolver` con prefix `/` y suffix `.jsp` ([L20-L23](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/spring-servlet.xml#L20-L23)): las vistas son JSP en la raíz pública de `WebContent/`.
- `<mvc:default-servlet-handler/>` ([L26](../../legacy/java/jakarta-ee/student-web-app/WebContent/WEB-INF/spring-servlet.xml#L26)).

## `applicationContext-service.xml` (huérfano)

**Ruta:** [resources/applicationContext-service.xml](../../legacy/java/jakarta-ee/student-web-app/resources/applicationContext-service.xml) (Ant lo copia a `WEB-INF/classes`) · **Líneas:** 16 · **Beans declarados:** 1

- `schemaNameProperties` (`PropertiesFactoryBean`) carga `classpath:org/sample/azure/student/coreft/persistence/xml/ucm_schema.properties` ([L8-L14](../../legacy/java/jakarta-ee/student-web-app/resources/applicationContext-service.xml#L8-L14)).
- **No se carga nunca**: no lo importan ni `web.xml` ni `applicationContext.xml`, y ninguna clase lo referencia.
- **`ucm_schema.properties` no existe** en el repositorio. Si alguien lo importa al migrar, el arranque fallará.
- Pendiente de confirmar con el cliente; candidato a eliminarse como código muerto.

## Configuración fuera de Spring

| Configuración | Dónde | Observación |
| --- | --- | --- |
| DataSource `jdbc/StudentDB` | [liberty_config/server-docker.xml L35-L41](../../legacy/java/jakarta-ee/student-web-app/liberty_config/server-docker.xml#L35-L41) | Pool del servidor (2-10 conexiones). URL, usuario y password llegan por variables de entorno |
| Mail session `mail/StudentMailSession` | [liberty_config/server-docker.xml L22-L27](../../legacy/java/jakarta-ee/student-web-app/liberty_config/server-docker.xml#L22-L27) | SMTP `localhost:25` con credenciales en texto plano |
| iBATIS | [resources/sql-map-config.xml](../../legacy/java/jakarta-ee/student-web-app/resources/sql-map-config.xml) | Lo inicializa `MyBatisUtil` en un bloque `static`, no Spring |
| Logging | [resources/log4j.properties](../../legacy/java/jakarta-ee/student-web-app/resources/log4j.properties) | log4j 1.x, a consola y a archivo `/logs/applog/...` |
| Variables de entorno | `liberty_config/server-docker.env`, `Dockerfile`, `docker-compose.yml` | Contienen secretos por defecto (ver [blockers.md](../blockers.md)) |

## Anotaciones vs XML

| Tipo | Cantidad | Detalle |
| --- | --- | --- |
| Componentes con anotaciones | 3 | `StudentController`, `AddStudentController` (`@Controller`), `StudentService` (`@Service`) |
| `<bean>` en XML | 2 | `InternalResourceViewResolver` (activo), `schemaNameProperties` (huérfano) |
| Elementos de namespace en XML | 5 | `component-scan` ×3, `mvc:annotation-driven`, `mvc:default-servlet-handler` |
| Servlets y listeners en `web.xml` | 5 | `DispatcherServlet`, 3 servlets, `ContextLoaderListener` |

**Ratio:** 60% anotaciones frente a 40% XML contando beans (75% / 25% si solo cuentan los activos). Todos los componentes de aplicación ya usan anotaciones; en XML solo queda infraestructura MVC.

> Convertir el XML a `@Configuration` o a la autoconfiguración de Spring Boot 3 es **poco trabajo**. El esfuerzo real está en `web.xml` (servlets clásicos y JNDI) y en la persistencia, que hoy no gestiona Spring.

## Equivalencias de referencia (la decisión del target es de Fase 2)

| Elemento actual | Equivalente habitual en Spring Boot 3 |
| --- | --- |
| `ContextLoaderListener` + `DispatcherServlet` en `web.xml` | Autoconfiguración con `@SpringBootApplication` (un único contexto) |
| 3 × `component-scan` | Escaneo implícito desde el paquete de la clase principal |
| `mvc:annotation-driven` | `WebMvcAutoConfiguration` |
| `InternalResourceViewResolver` (JSP) | `spring.mvc.view.prefix/suffix`. **JSP no funciona con JAR ejecutable** (ver [blockers.md](../blockers.md)) |
| `mvc:default-servlet-handler` | Recursos estáticos en `classpath:/static` |
| Servlets clásicos en `web.xml` | `@Controller` o `ServletRegistrationBean` |
| `resource-ref` JNDI (DataSource, Mail) | `spring.datasource.*` y `spring.mail.*` con variables de entorno o Key Vault |
| `schemaNameProperties` | Eliminar (huérfano) |
