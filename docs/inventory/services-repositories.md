# Inventario: services, repositories y DAOs

> **Sistema analizado:** `legacy/java/jakarta-ee/student-web-app` · **Fase 1 (assessment)** · 2026-10-04

## Resumen

| Tipo | Cantidad |
| --- | --- |
| `@Service` | 1 |
| `@Repository` / DAOs | 0 |
| `@Component` | 0 |
| Utilidades de acceso a datos | 1 (`MyBatisUtil`, singleton estático) |
| Puntos del código que abren sesión iBATIS | 5 (2 en el service, 3 en servlets) |
| Clases de dominio | 1 (`StudentProfile`) |

## Services

| Service | Implementa interface | Métodos públicos | Transaccional | Archivo |
| --- | --- | --- | --- | --- |
| StudentService | No | 2 | Sin `@Transactional`. `saveStudent` usa una transacción manual de iBATIS | [service/StudentService.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java) |

| Método | Línea | Operación | Manejo de errores |
| --- | --- | --- | --- |
| `List<StudentProfile> getAllStudents()` | [L19](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java#L19) | `queryForList("com.azure.sample.StudentMapper.listStudent")` | **Atrapa cualquier excepción y devuelve una lista vacía** ([L28-L31](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java#L28-L31)) |
| `boolean saveStudent(String name, String email, String major)` | [L45](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java#L45) | `startTransaction`, `insert(...addStudent)`, `commitTransaction`; `endTransaction` si falla | Devuelve `false`; el detalle solo queda en el log |

Observaciones:
- No implementa interface. No es un problema por sí mismo, pero complica los mocks en tests (y hoy no hay tests).
- `getAllStudents` se traga los errores de BD. En el flujo Spring MVC, un fallo de BD aparece como "No student profiles found." y el `catch` de `StudentController` nunca se ejecuta.
- No envía email. El flujo legacy (`AddStudentServlet`) sí lo hace (ver [features/02](../features/02-registro-estudiante.md)).
- Escribe en el log datos personales sin sanear: nombre, email y carrera ([L46](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java#L46)).

## Repositories / DAOs

| Repository | Tipo | Tecnología | Métodos | Archivo |
| --- | --- | --- | --- | --- |
| — | — | — | — | No hay. `applicationContext.xml` escanea `coreft.dao`, pero ese paquete no existe |

El acceso a datos usa **directamente** la API de iBATIS 2 desde 5 puntos:

| Llamador | Líneas | Statement | Transacción |
| --- | --- | --- | --- |
| `StudentService.getAllStudents` | [L25-L26](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java#L25-L26) | `listStudent` | — |
| `StudentService.saveStudent` | [L51-L62](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/service/StudentService.java#L51-L62) | `addStudent` | Manual |
| `IndexServlet.doGet` | [L30-L31](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/IndexServlet.java#L30-L31) | `listStudent` | — |
| `AddStudentServlet.doPost` | [L46-L55](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/AddStudentServlet.java#L46-L55) | `addStudent` | Manual |
| `StudentProfileListServlet.doGet` | [L39-L42](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfileListServlet.java#L39-L42) | `listStudent` | — |

## Utilidades y dominio

| Clase | Rol | Observación | Archivo |
| --- | --- | --- | --- |
| MyBatisUtil | Fábrica estática de `SqlMapClient`. Es iBATIS 2, **no** MyBatis 3, aunque el nombre diga otra cosa | Se inicializa en un bloque `static` ([L13-L25](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/util/MyBatisUtil.java#L13-L25)). Si falla, escribe en `System.out/err`, deja `null` y cada petición posterior lanza `RuntimeException`. El `Reader` nunca se cierra | [util/MyBatisUtil.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/util/MyBatisUtil.java) |
| StudentProfile | POJO de dominio `Serializable` con `id`, `name`, `email`, `major` | Sin anotaciones de persistencia ni de validación | [StudentProfile.java](../../legacy/java/jakarta-ee/student-web-app/src/org/sample/azure/student/coreft/StudentProfile.java) |

## Patrones problemáticos (checklist Spring legacy)

| Patrón | ¿Presente? | Detalle |
| --- | --- | --- |
| `HibernateTemplate` | No | — |
| `HibernateDaoSupport` / `JdbcDaoSupport` | No | — |
| `SqlMapClientTemplate` / `SqlMapClientDaoSupport` (soporte iBATIS de Spring, eliminado en Spring 4) | No | iBATIS se usa sin ninguna integración con Spring |
| DAOs con SQL inline | No | El SQL está en `Student_SqlMap.xml` |
| DAOs sin interface | N/A | No hay DAOs |
| Service locator / singleton estático | **Sí** | `MyBatisUtil.getSqlMapClient()` impide la inyección de dependencias y los tests |
| Lógica de datos duplicada fuera del service | **Sí** | 3 servlets se saltan la capa de servicio |
| Transacciones manuales | **Sí** | `startTransaction/commitTransaction/endTransaction` en 2 sitios |
| Inyección por campo | Sí | 2 `@Autowired` en los controllers |
