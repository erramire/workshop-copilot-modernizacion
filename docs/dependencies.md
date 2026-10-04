# Grafo de dependencias: student-web-app

> **Sistema analizado:** `legacy/java/jakarta-ee/student-web-app` · **Fase 1 (assessment)** · 2026-10-04

## 1. Componentes en runtime

```mermaid
flowchart LR
    U["Navegador"] --> LIB["Open Liberty 25.0.0.7<br/>context root /"]

    subgraph WAR["OpenLibertyApp.war"]
        subgraph SPRING["Spring MVC (DispatcherServlet en /app/*)"]
            SC["StudentController<br/>GET /app/ y /app/students"]
            AC["AddStudentController<br/>GET y POST /app/add-student"]
            SS["StudentService"]
        end
        subgraph LEGACY["Servlets clásicos (web.xml)"]
            IS["IndexServlet<br/>GET /"]
            AS["AddStudentServlet<br/>GET y POST /addStudent"]
            PL["StudentProfileListServlet<br/>GET /studentProfileList"]
        end
        MU["MyBatisUtil<br/>SqlMapClient estático"]
        SM["sql-map-config.xml<br/>Student_SqlMap.xml"]
        JSP["JSP: index, spring-index,<br/>spring-add-student, add_student_profile"]
        FIL["CommonHttpServletFilter<br/>(sin registrar)"]
    end

    LIB --> SC
    LIB --> AC
    LIB --> IS
    LIB --> AS
    LIB --> PL
    SC --> SS
    AC --> SS
    SS --> MU
    IS --> MU
    AS --> MU
    PL --> MU
    MU --> SM
    SC --> JSP
    AC --> JSP
    IS --> JSP
    AS --> JSP

    MU -->|"JNDI jdbc/StudentDB"| DB[("MySQL 8.0<br/>student_profiles")]
    AS -->|"JNDI mail/StudentMailSession"| SMTP["SMTP localhost:25"]

    style FIL stroke-dasharray: 5 5
```

Lo que muestra el grafo:
- Los 3 servlets clásicos **se saltan `StudentService`** y van directamente a `MyBatisUtil`.
- Solo `AddStudentServlet` usa el correo.
- `MyBatisUtil` es el único punto de acceso a la BD, pero es estático y no lo gestiona Spring.

## 2. Librerías

```mermaid
flowchart TD
    APP["Código de la app<br/>9 clases Java y 4 JSP"]

    APP --> WEBMVC["spring-webmvc 5.3.23"]
    APP --> WEB["spring-web 5.3.23"]
    APP --> CTX["spring-context 5.3.23"]
    APP --> IB["ibatis-sqlmap 2.3.0"]
    APP --> L4J["log4j 1.2.17"]
    APP --> JM["jackson-mapper-asl 1.9.13"]
    APP -.->|provided| SAPI["javax.servlet-api 4.0.1"]
    APP -.->|provided| MAPI["javax.mail-api 1.6.2"]

    WEBMVC --> WEB
    WEBMVC --> CTX
    WEB --> BEANS["spring-beans 5.3.23"]
    CTX --> AOP["spring-aop 5.3.23"]
    CTX --> BEANS
    CTX --> CORE["spring-core 5.3.23"]
    AOP --> BEANS
    BEANS --> CORE
    CTX -.->|"no está en WEB-INF/lib"| EXP["spring-expression"]
    CORE -.->|"no está en WEB-INF/lib"| JCL["spring-jcl"]
    JM --> JC["jackson-core-asl 1.9.13"]
    IB -.->|"DataSource JNDI de Liberty"| MYSQL["mysql-connector-j 8.0.33<br/>librería compartida de Liberty"]

    classDef eol fill:#fde2e2,stroke:#c0392b
    classDef missing fill:#ffffff,stroke:#c0392b,stroke-dasharray: 5 5
    class IB,L4J,JM,JC eol
    class EXP,JCL missing
```

Leyenda: en rojo, las librerías EOL o retiradas; en borde discontinuo, las dependencias transitivas obligatorias que faltan (ver [inventory/dependencies-pom.md](inventory/dependencies-pom.md)).

## 3. Dependencias por clase

| Clase | Clases internas que usa | Librerías y APIs externas |
| --- | --- | --- |
| `controller.StudentController` | `StudentService`, `StudentProfile` | spring-context, spring-beans, spring-web, spring-webmvc, log4j |
| `controller.AddStudentController` | `StudentService` | spring-context, spring-beans, spring-web, spring-webmvc, log4j |
| `service.StudentService` | `MyBatisUtil`, `StudentProfile` | spring-context, iBATIS, log4j |
| `IndexServlet` | `MyBatisUtil`, `StudentProfile` | `javax.servlet`, iBATIS, log4j |
| `AddStudentServlet` | `MyBatisUtil` | `javax.servlet`, `javax.mail`, `javax.naming` (JNDI), iBATIS, log4j |
| `StudentProfileListServlet` | `MyBatisUtil`, `StudentProfile` | `javax.servlet`, iBATIS, Jackson 1.x, log4j |
| `util.MyBatisUtil` | — | iBATIS (`sql-map-config.xml`) |
| `filter.CommonHttpServletFilter` | — | `javax.servlet` |
| `StudentProfile` | — | — |

## 4. Dependencias del servidor (Open Liberty)

| Recurso | Lo define | Lo consume | Qué cambia sin Liberty |
| --- | --- | --- | --- |
| DataSource `jdbc/StudentDB` | `server-docker.xml` L35-L41 | `sql-map-config.xml` (iBATIS) | No hay JNDI: hay que configurar la conexión en la app |
| Mail session `mail/StudentMailSession` | `server-docker.xml` L22-L27 | `AddStudentServlet` (`java:comp/env`) | No hay JNDI: hay que configurar el correo en la app |
| Librería compartida `mysql-lib` | `server-docker.xml` L31-L33 y `Dockerfile` L14 | Driver JDBC | Pasa a ser una dependencia del build |
| Context root `/` | `server-docker.xml` L43 | Enlaces absolutos en las JSP | Hay que mantener `/` o actualizar los enlaces |
| Features Java EE 8 | `server-docker.xml` L3-L13 | Servlet 4.0, JSP 2.3, JavaMail 1.6 | Los sustituyen el contenedor embebido y las librerías |
