package org.sample.azure.student.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sample.azure.student.application.StudentRegisteredEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SmtpWelcomeNotifierTest {

    private static final StudentRegisteredEvent STUDENT =
            new StudentRegisteredEvent(7, "Alice Johnson", "alice@example.com");

    @Mock
    private JavaMailSender mailSender;

    private SmtpWelcomeNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new SmtpWelcomeNotifier(mailSender, "noreply@example.com");
    }

    @Test
    @DisplayName("AC-13: el correo lleva el remitente, el destinatario, el asunto y el cuerpo del legacy")
    void sendsLegacyWelcomeMessage() {
        notifier.sendWelcome(STUDENT);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("noreply@example.com");
        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getSubject()).isEqualTo("Welcome, Alice Johnson!");
        assertThat(message.getText()).isEqualTo(
                "Dear Alice Johnson,\n\nYour student profile has been created successfully.\n\nRegards,\nAdmin");
    }

    @Test
    @DisplayName("AC-14: un fallo de envío no se propaga y se registra sin el email")
    void sendFailureIsLoggedWithoutEmail(CapturedOutput output) {
        doThrow(new MailSendException("Invalid address alice@example.com"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> notifier.sendWelcome(STUDENT)).doesNotThrowAnyException();

        assertThat(output)
                .contains("Welcome email failed for student id=7")
                .doesNotContain("alice@example.com");
    }
}
