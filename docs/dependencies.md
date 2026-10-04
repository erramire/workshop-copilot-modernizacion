# Grafo de dependencias: student-web-app

> Sistema: `legacy/java/jakarta-ee/student-web-app/`

## Componentes en runtime

```mermaid
flowchart LR
  U(("Usuario"))

  subgraph LIB["Open Liberty 25 (Java EE 8)"]
    subgraph SRV["Servlets en web.xml"]
      IS["IndexServlet<br/>GET /"]
      AS["AddStudentServlet<br/>GET, POST /addStudent"]
      LS["StudentProfileListServlet<br/>GET /studentProfileList"]
    end
    subgraph MVC["Spring MVC 5.3.23 en /app/*"]
      SC["StudentController<br/>GET /app/ y /app/students"]
      AC["AddStudentController<br/>GET, POST /app/add-student"]
    end
    SVC["StudentService"]
    MBU["MyBatisUtil<br/>SqlMapClient estático"]
    CFG[("sql-map-config.xml<br/>Student_SqlMap.xml")]
    subgraph JSP["Vistas JSP"]
      J1["index.jsp"]
      J2["spring-index.jsp"]
      J3["spring-add-student.jsp"]
      J4["add_student_profile.jsp"]
    end
    F["CommonHttpServletFilter<br/>no registrado"]:::dead
  end

  DB[("MySQL 8.0<br/>student_profiles")]
  SMTP[["SMTP<br/>mail/StudentMailSession"]]

  U --> IS
  U --> AS
  U --> LS
  U --> SC
  U --> AC
  SC --> SVC
  AC --> SVC
  IS --> MBU
  AS --> MBU
  LS --> MBU
  SVC --> MBU
  MBU --> CFG
  MBU -- "JNDI jdbc/StudentDB" --> DB
  AS -- "JNDI + javax.mail" --> SMTP
  IS --> J1
  SC --> J2
  AC --> J3
  AC -. "redirect a /app/" .-> SC
  AS --> J4

  classDef dead stroke-dasharray:5 5,color:#999
```

**Lectura:** las dos pilas web (servlets y Spring MVC) llegan al mismo punto de acceso a datos estático (`MyBatisUtil`). Solo la pila Spring pasa por `StudentService`, y solo `AddStudentServlet` usa SMTP.

## Librerías y plataforma

```mermaid
flowchart TD
  APP["OpenLibertyApp.war"]
  APP --> SP["Spring Framework 5.3.23<br/>core, beans, context, aop, web, webmvc"]:::eol
  APP --> IB["iBATIS SqlMaps 2.3.0"]:::eol
  APP --> JK["Jackson 1.9.13 (codehaus)"]:::eol
  APP --> L4["Log4j 1.2.17"]:::eol
  SP -. "requeridas, faltan en el WAR" .-> MISS["spring-expression<br/>spring-jcl"]:::warn
  APP -. "provisto por Liberty" .-> EE["Servlet 4.0, JSP 2.3, JavaMail 1.6<br/>(javax.*)"]
  IB -. "lookup JNDI" .-> DS["DataSource jdbc/StudentDB<br/>(server.xml)"]
  DS --> CJ["MySQL Connector/J 8.0.33<br/>librería compartida de Liberty"]:::cve

  classDef eol fill:#f8d7da,stroke:#c82333,color:#000
  classDef warn fill:#fff3cd,stroke:#d39e00,color:#000
  classDef cve fill:#ffe5b4,stroke:#e67e22,color:#000
```

Leyenda: rojo = sin soporte (EOL o sin parches OSS). Amarillo = riesgo pendiente de validar. Naranja = versión con CVE.

## Dependencias entre features

```mermaid
flowchart LR
  F2["02 Alta de estudiante"] -- "la ruta Spring redirige al listado" --> F1["01 Consulta de estudiantes"]
  F2 -- "solo la ruta servlet" --> F3["03 Notificación de bienvenida"]
```

## Matriz componente → recurso

| Componente | MySQL | SMTP | Jackson 1.x | Log4j 1.x | Spring |
| --- | --- | --- | --- | --- | --- |
| IndexServlet | ✔ (directo) | | | ✔ | |
| AddStudentServlet | ✔ (directo) | ✔ | | ✔ | |
| StudentProfileListServlet | ✔ (directo) | | ✔ | ✔ | |
| StudentController | ✔ (vía service) | | | ✔ | ✔ |
| AddStudentController | ✔ (vía service) | | | ✔ | ✔ |
| StudentService | ✔ | | | ✔ | ✔ |
| MyBatisUtil | ✔ | | | (usa `System.out`) | |
| CommonHttpServletFilter | | | | | |

## Configuración externa que consume la app

| Recurso | Dónde se define | Cómo lo consume la app |
| --- | --- | --- |
| `jdbc/StudentDB` | `liberty_config/server-docker.xml:35-41`, con las variables `JDBC_URL`, `DB_USER` y `DB_PASSWORD` | iBATIS por JNDI (`sql-map-config.xml:9-10`) |
| `mail/StudentMailSession` | `liberty_config/server-docker.xml:22-27` | `InitialContext.lookup` (`AddStudentServlet.java:98-99`) |
| Driver MySQL | `liberty_config/server-docker.xml:31-33` y `Dockerfile:14` | Librería compartida del servidor |
| Ruta del log | `resources/log4j.properties:11` | Log4j `RollingFileAppender` |
