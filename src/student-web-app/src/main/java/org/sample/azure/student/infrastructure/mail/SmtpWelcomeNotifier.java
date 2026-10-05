package org.sample.azure.student.infrastructure.mail;

import org.sample.azure.student.application.StudentRegisteredEvent;
import org.sample.azure.student.application.WelcomeNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mail.welcome", name = "enabled", havingValue = "true")
public class SmtpWelcomeNotifier implements WelcomeNotifier {

    private static final Logger log = LoggerFactory.getLogger(SmtpWelcomeNotifier.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpWelcomeNotifier(JavaMailSender mailSender, @Value("${app.mail.welcome.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendWelcome(StudentRegisteredEvent student) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(student.email());
        message.setSubject("Welcome, " + student.name() + "!");
        message.setText("Dear " + student.name()
                + ",\n\nYour student profile has been created successfully.\n\nRegards,\nAdmin");
        // El alta ya está confirmada: un fallo de envío solo se registra, sin la dirección (ADR-011)
        try {
            mailSender.send(message);
            log.info("Welcome email sent for student id={}", student.id());
        } catch (RuntimeException ex) {
            log.warn("Welcome email failed for student id={} ({})", student.id(), ex.getClass().getSimpleName());
        }
    }
}
