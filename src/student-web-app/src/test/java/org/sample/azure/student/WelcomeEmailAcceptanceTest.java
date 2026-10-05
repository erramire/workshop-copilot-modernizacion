package org.sample.azure.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.sample.azure.student.infrastructure.persistence.StudentProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "app.mail.welcome.enabled=true")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class WelcomeEmailAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentProfileRepository repository;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("AC-13: con el envío activado, cada alta confirmada envía el correo de bienvenida")
    void registrationSendsWelcomeEmailAfterCommit() throws Exception {
        register("Alice Johnson", "alice@example.com", "Biology").andExpect(status().isFound());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("alice@example.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("Welcome, Alice Johnson!");
        assertThat(captor.getValue().getFrom()).isEqualTo("noreply@example.com");
    }

    @Test
    @DisplayName("AC-14: si el envío falla, el alta se mantiene y el usuario ve el mensaje de éxito")
    void mailFailureDoesNotAffectRegistration(CapturedOutput output) throws Exception {
        doThrow(new MailSendException("SMTP failure for alice@example.com"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        register("Alice Johnson", "alice@example.com", "Biology")
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/app/"))
                .andExpect(flash().attribute("successMessage", "Student Alice Johnson has been added successfully!"));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(output)
                .contains("Welcome email failed for student id=")
                .doesNotContain("alice@example.com");
    }

    private ResultActions register(String name, String email, String major) throws Exception {
        return mockMvc.perform(post("/app/add-student").with(csrf())
                .param("name", name)
                .param("email", email)
                .param("major", major));
    }
}
