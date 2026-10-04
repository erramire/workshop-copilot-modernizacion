# Grafo de dependencias — Student Web App

Ruta base: `legacy/java/jakarta-ee/student-web-app/`

## 1. Componentes y flujo de llamadas

```mermaid
flowchart LR
    U(["Navegador"])

    subgraph LIB["Open Liberty 25.0.0.7 · context-root /"]
        IS["IndexServlet<br/>GET /"]
        AS["AddStudentServlet<br/>GET/POST /addStudent"]
        LS["StudentProfileListServlet<br/>GET /studentProfileList"]
        DS["DispatcherServlet<br/>/app/*"]
        SC["StudentController<br/>GET /app/ y /app/students"]
        AC["AddStudentController<br/>GET/POST /app/add-student"]
        SS["StudentService"]
        MU["MyBatisUtil<br/>SqlMapClient estático (iBATIS 2)"]
        CFG["sql-map-config.xml<br/>Student_SqlMap.xml"]
        JDB[("JNDI jdbc/StudentDB")]
        JMAIL["JNDI mail/StudentMailSession"]
        V1["index.jsp"]
        V2["add_student_profile.jsp"]
        V3["spring-index.jsp"]
        V4["spring-add-student.jsp"]
        F["CommonHttpServletFilter<br/>(no registrado)"]
    end

    DB[("MySQL 8.0<br/>student_profiles")]
    SMTP["SMTP localhost:25"]

    U --> IS
    U --> AS
    U --> LS
    U --> DS
    DS --> SC
    DS --> AC
    SC --> SS
    AC --> SS
    SS --> MU
    IS --> MU
    AS --> MU
    LS --> MU
    MU --> CFG
    CFG --> JDB
    JDB --> DB
    AS --> JMAIL
    JMAIL --> SMTP
    IS --> V1
    AS --> V2
    SC --> V3
    AC --> V4

    classDef dead stroke-dasharray: 5 5
    class F dead
```

**Lectura del grafo:**

- Hay dos pilas paralelas: servlets (sin Spring) y Spring MVC. Solo Spring MVC pasa por `StudentService`.
- Todos los caminos convergen en `MyBatisUtil`, que guarda estado estático: acoplamiento fuerte y sin inyección de dependencias.
- Solo `AddStudentServlet` depende del servidor de correo.

## 2. Librerías y runtime

```mermaid
flowchart TB
    WAR["OpenLibertyApp.war"]

    subgraph LIBS["WEB-INF/lib (dentro del WAR)"]
        SWMVC["spring-webmvc 5.3.23"]
        SW["spring-web 5.3.23"]
        SCTX["spring-context 5.3.23"]
        SAOP["spring-aop 5.3.23"]
        SB["spring-beans 5.3.23"]
        SCORE["spring-core 5.3.23"]
        IB["ibatis-sqlmap 2.3.0"]
        JM["jackson-mapper-asl 1.9.13"]
        JC["jackson-core-asl 1.9.13"]
        L4J["log4j 1.2.17"]
    end

    subgraph MISSING["Requeridas por Spring 5.3 y ausentes del WAR"]
        SEXP["spring-expression"]
        SJCL["spring-jcl"]
    end

    subgraph SERVER["Provistas por Open Liberty"]
        SERV["javax.servlet 4.0 / JSP 2.3"]
        MAIL["javax.mail 1.6"]
        JNDI["JNDI + JDBC 4.3"]
        MYSQL["mysql-connector-j 8.0.33<br/>(librería compartida)"]
    end

    WAR --> SWMVC
    WAR --> IB
    WAR --> JM
    WAR --> L4J
    WAR --> SERV
    WAR --> MAIL
    WAR --> JNDI
    SWMVC --> SW
    SWMVC --> SCTX
    SWMVC --> SEXP
    SW --> SB
    SCTX --> SAOP
    SCTX --> SEXP
    SAOP --> SB
    SB --> SCORE
    SCORE --> SJCL
    JM --> JC
    JNDI --> MYSQL

    classDef missing stroke-dasharray: 5 5
    class SEXP,SJCL missing
```

Ver [blockers.md#b-02](blockers.md#b-02) sobre los JARs ausentes.

## 3. Matriz clase × dependencia

| Clase | Spring | iBATIS | log4j 1 | Jackson 1 | `javax.servlet` | `javax.mail` | JNDI |
| --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| `IndexServlet` | | ✔ | ✔ | | ✔ | | |
| `AddStudentServlet` | | ✔ | ✔ | | ✔ | ✔ | ✔ |
| `StudentProfileListServlet` | | ✔ | ✔ | ✔ | ✔ | | |
| `CommonHttpServletFilter` | | | | | ✔ | | |
| `StudentController` | ✔ | | ✔ | | | | |
| `AddStudentController` | ✔ | | ✔ | | | | |
| `StudentService` | ✔ | ✔ | ✔ | | | | |
| `MyBatisUtil` | | ✔ | | | | | ✔ (vía iBATIS) |
| `StudentProfile` | | | | | | | |

## 4. Dependencias externas en tiempo de ejecución

| Dependencia | Cómo se obtiene | La usa |
| --- | --- | --- |
| MySQL `studentdb` | JNDI `jdbc/StudentDB` (Liberty) | F-01, F-02 |
| Servidor SMTP | JNDI `mail/StudentMailSession` (Liberty) | F-03 |
| Sistema de archivos `/logs/applog/...` | `RollingFileAppender` de log4j | Logging |
