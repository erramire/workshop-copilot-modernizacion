package org.sample.azure.student.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sample.azure.student.application.StudentService;
import org.sample.azure.student.config.SecurityConfig;
import org.sample.azure.student.domain.StudentProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StudentController.class)
@Import(SecurityConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @ParameterizedTest
    @ValueSource(strings = {"/app", "/app/", "/app/students"})
    @DisplayName("AC-01: las URLs del listado muestran la tabla con todos los registros")
    void listShowsAllStudents(String path) throws Exception {
        when(studentService.listStudents()).thenReturn(List.of(
                student(1, "Alice Johnson", "alice@example.com", "Biology"),
                student(2, "Bob Smith", "bob@example.com", "Physics")));

        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(view().name("students/list"))
                .andExpect(content().string(containsString("<th>Major</th>")))
                .andExpect(content().string(containsString("Alice Johnson")))
                .andExpect(content().string(containsString("bob@example.com")));
    }

    @Test
    @DisplayName("AC-02: sin registros se muestra 'No student profiles found.'")
    void emptyListShowsPlaceholderRow() throws Exception {
        when(studentService.listStudents()).thenReturn(List.of());

        mockMvc.perform(get("/app/students"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No student profiles found.")));
    }

    @Test
    @DisplayName("AC-03: si la base de datos falla se muestra un aviso genérico, la tabla vacía y el error queda en el log como ERROR")
    void databaseFailureShowsGenericMessage(CapturedOutput output) throws Exception {
        when(studentService.listStudents())
                .thenThrow(new DataAccessResourceFailureException("Connection refused to db-host:3306"));

        mockMvc.perform(get("/app/students"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Unable to load student data.")))
                .andExpect(content().string(not(containsString("Connection refused"))))
                .andExpect(content().string(not(containsString("No student profiles found."))));

        assertThat(output.getAll()).containsPattern("ERROR .*Unable to load students");
    }

    @Test
    @DisplayName("AC-04: los valores se escapan al pintarse")
    void valuesAreHtmlEscaped() throws Exception {
        when(studentService.listStudents())
                .thenReturn(List.of(student(1, "<script>alert(1)</script>", "x@example.com", "History")));

        mockMvc.perform(get("/app/students"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    @DisplayName("AC-05: el formulario muestra los tres campos obligatorios y el token CSRF")
    void formShowsRequiredFieldsAndCsrfToken() throws Exception {
        mockMvc.perform(get("/app/add-student"))
                .andExpect(status().isOk())
                .andExpect(view().name("students/form"))
                .andExpect(content().string(containsString("Student Name:")))
                .andExpect(content().string(containsString("Email Address:")))
                .andExpect(content().string(containsString("Major/Field of Study:")))
                .andExpect(content().string(containsString("type=\"email\"")))
                .andExpect(content().string(containsString("name=\"major\"")))
                .andExpect(content().string(containsString("required placeholder=\"Enter student's full name\"")))
                .andExpect(content().string(containsString("required placeholder=\"Enter student's email\"")))
                .andExpect(content().string(containsString("required placeholder=\"Enter student's major\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    @DisplayName("AC-06: un alta válida guarda, redirige a /app/ y deja el mensaje de éxito")
    void validSubmissionRegistersAndRedirects() throws Exception {
        mockMvc.perform(post("/app/add-student").with(csrf())
                        .param("name", "Alice Johnson")
                        .param("email", "alice@example.com")
                        .param("major", "Biology"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/app/"))
                .andExpect(flash().attribute("successMessage", "Student Alice Johnson has been added successfully!"));

        verify(studentService).register("Alice Johnson", "alice@example.com", "Biology");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSubmissions")
    @DisplayName("AC-07: los datos inválidos vuelven al formulario con errores y no se guarda nada")
    void invalidSubmissionShowsFieldErrors(String description, String name, String email, String major,
                                           String fieldWithError) throws Exception {
        mockMvc.perform(post("/app/add-student").with(csrf())
                        .param("name", name)
                        .param("email", email)
                        .param("major", major))
                .andExpect(status().isOk())
                .andExpect(view().name("students/form"))
                .andExpect(model().attributeHasFieldErrors("studentForm", fieldWithError));

        verify(studentService, never()).register(any(), any(), any());
    }

    static Stream<Arguments> invalidSubmissions() {
        return Stream.of(
                Arguments.of("nombre en blanco", "   ", "alice@example.com", "Biology", "name"),
                Arguments.of("carrera vacía", "Alice", "alice@example.com", "", "major"),
                Arguments.of("email sin formato válido", "Alice", "not-an-email", "Biology", "email"),
                Arguments.of("varias direcciones de email", "Alice", "a@example.com, b@example.com", "Biology", "email"),
                Arguments.of("nombre de más de 255 caracteres", "x".repeat(256), "alice@example.com", "Biology", "name"));
    }

    @Test
    @DisplayName("AC-07: el formulario conserva lo introducido cuando hay errores")
    void invalidSubmissionKeepsEnteredValues() throws Exception {
        mockMvc.perform(post("/app/add-student").with(csrf())
                        .param("name", "Alice Johnson")
                        .param("email", "not-an-email")
                        .param("major", "Biology"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Alice Johnson\"")))
                .andExpect(content().string(containsString("value=\"not-an-email\"")));
    }

    @Test
    @DisplayName("AC-07: los espacios al principio y al final se recortan")
    void valuesAreTrimmedBeforeRegistering() throws Exception {
        mockMvc.perform(post("/app/add-student").with(csrf())
                        .param("name", "  Alice Johnson  ")
                        .param("email", " alice@example.com ")
                        .param("major", " Biology "))
                .andExpect(status().isFound());

        verify(studentService).register("Alice Johnson", "alice@example.com", "Biology");
    }

    @Test
    @DisplayName("AC-09: un POST sin token CSRF recibe 403 y no se guarda nada")
    void submissionWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/app/add-student")
                        .param("name", "Alice Johnson")
                        .param("email", "alice@example.com")
                        .param("major", "Biology"))
                .andExpect(status().isForbidden());

        verify(studentService, never()).register(any(), any(), any());
    }

    @Test
    @DisplayName("AC-10: si la base de datos falla al guardar se redirige con un mensaje genérico")
    void databaseFailureOnSaveShowsGenericMessage() throws Exception {
        when(studentService.register(any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("Connection refused to db-host:3306"));

        mockMvc.perform(post("/app/add-student").with(csrf())
                        .param("name", "Alice Johnson")
                        .param("email", "alice@example.com")
                        .param("major", "Biology"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/app/"))
                .andExpect(flash().attribute("errorMessage", "Failed to save student. Please try again."))
                .andExpect(flash().attributeCount(1));
    }

    private static StudentProfile student(int id, String name, String email, String major) {
        StudentProfile profile = new StudentProfile(name, email, major);
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }
}
