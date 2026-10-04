# Inventario de services y repositories

Ruta base: `legacy/java/jakarta-ee/student-web-app/`

## Services

| Service | Implementa interface | Métodos públicos | Transaccional | Archivo |
| --- | --- | --- | --- | --- |
| `StudentService` (`@Service`) | No | 2 | No (sin `@Transactional`; transacción manual de iBATIS en `saveStudent`) | `src/org/sample/azure/student/coreft/service/StudentService.java` |

| Método | Operación | Manejo de errores | Consumidores |
| --- | --- | --- | --- |
| `List<StudentProfile> getAllStudents()` | `queryForList("com.azure.sample.StudentMapper.listStudent")` | Traga la excepción y devuelve una lista vacía | `StudentController.index`, `StudentController.listStudents` |
| `boolean saveStudent(String name, String email, String major)` | `startTransaction` → `insert("...addStudent", Map)` → `commitTransaction` | `endTransaction` (rollback) y devuelve `false` | `AddStudentController.addStudent` |

- Los servlets **no** usan este service: duplican la lógica llamando directamente a `MyBatisUtil`.
- Depende estáticamente de `MyBatisUtil`, así que no se puede reemplazar por un mock sin refactorizar.

## Repositories / DAOs

| Repository | Tipo | Tecnología | Métodos | Archivo |
| --- | --- | --- | --- | --- |
| — | — | — | — | No hay DAOs. `applicationContext.xml` escanea `org.sample.azure.student.coreft.dao`, que no existe |
| `MyBatisUtil` (utilitario, no DAO) | Clase con estado estático | iBATIS 2 `SqlMapClient` | 1 (`getSqlMapClient()`) | `src/org/sample/azure/student/coreft/util/MyBatisUtil.java` |

### Puntos de acceso a datos

| Clase | Método | Statement | Operación | Transacción |
| --- | --- | --- | --- | --- |
| `StudentService` | `getAllStudents` | `listStudent` | `queryForList` | Implícita (autocommit) |
| `StudentService` | `saveStudent` | `addStudent` | `insert` | Manual |
| `IndexServlet` | `doGet` | `listStudent` | `queryForList` | Implícita |
| `AddStudentServlet` | `doPost` | `addStudent` | `insert` | Manual |
| `StudentProfileListServlet` | `doGet` | `listStudent` | `queryForList` | Implícita |

Hay 5 puntos de llamada en 4 clases: `listStudent` se invoca desde 3 lugares y `addStudent` desde 2.

## Patrones problemáticos

| Patrón | Dónde | Riesgo | Acción sugerida |
| --- | --- | --- | --- |
| `HibernateTemplate` / `HibernateDaoSupport` / `JdbcDaoSupport` | — | No aplica | — |
| `SqlMapClient` de iBATIS fuera de Spring | `MyBatisUtil` | Sin soporte desde Spring 4.0; sin inyección de dependencias | Mapper de MyBatis 3, repository de Spring Data o `JdbcClient` como bean |
| Singleton estático inicializado en un bloque `static` | `MyBatisUtil` | Si falla la inicialización, fallan todas las requests; usa `System.out/err` para loguear | Bean gestionado por Spring, con health check |
| Acceso a datos desde la capa de presentación | 3 servlets | Duplicación y divergencia funcional | Consolidar en el service |
| Transacciones manuales | `StudentService.saveStudent`, `AddStudentServlet.doPost` | Inconsistencia y fugas si falta `endTransaction` | `@Transactional` |
| Excepciones tragadas y retorno `boolean` | `StudentService` | Errores silenciosos (BD caída = lista vacía) | Propagar y manejar en `@ControllerAdvice` |
| Inyección por campo (`@Autowired`) | 2 controllers | Dificulta los tests | Inyección por constructor |
| SQL inline en el código | — | No hay: el SQL vive en `Student_SqlMap.xml` | — |
