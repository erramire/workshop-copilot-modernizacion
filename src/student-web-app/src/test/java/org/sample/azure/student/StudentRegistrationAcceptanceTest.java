package org.sample.azure.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sample.azure.student.application.WelcomeNotifier;
import org.sample.azure.student.infrastructure.mail.NoOpWelcomeNotifier;
import org.sample.azure.student.infrastructure.persistence.StudentProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class StudentRegistrationAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentProfileRepository repository;

    @Autowired
    private WelcomeNotifier welcomeNotifier;

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("AC-06: el alta queda guardada y aparece en el listado con el mensaje de éxito")
    void registeredStudentAppearsInList() throws Exception {
        MvcResult result = register("Grace Hopper", "grace@example.com", "Computer Science")
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/app/"))
                .andReturn();

        mockMvc.perform(get("/app/").flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Student Grace Hopper has been added successfully!")))
                .andExpect(content().string(containsString("grace@example.com")));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-08: el mismo email se puede registrar dos veces")
    void duplicateEmailsAreAllowed() throws Exception {
        for (String name : List.of("Ada Lovelace", "Ada King")) {
            register(name, "ada@example.com", "Mathematics").andExpect(status().isFound());
        }

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-11: los logs del alta no contienen ni el nombre ni el email")
    void registrationLogsContainNoPersonalData(CapturedOutput output) throws Exception {
        register("Katherine Johnson", "katherine.pii@example.com", "Physics").andExpect(status().isFound());

        assertThat(output)
                .contains("Student registered with id=")
                .doesNotContain("Katherine Johnson")
                .doesNotContain("katherine.pii@example.com");
    }

    @Test
    @DisplayName("AC-12: por defecto el envío está desactivado y no existe ningún JavaMailSender")
    void welcomeEmailIsDisabledByDefault() throws Exception {
        register("Alan Turing", "alan@example.com", "Mathematics").andExpect(status().isFound());

        assertThat(welcomeNotifier).isInstanceOf(NoOpWelcomeNotifier.class);
        assertThat(context.getBeanProvider(JavaMailSender.class).getIfAvailable()).isNull();
    }

    private ResultActions register(String name, String email, String major) throws Exception {
        return mockMvc.perform(post("/app/add-student").with(csrf())
                .param("name", name)
                .param("email", email)
                .param("major", major));
    }
}
