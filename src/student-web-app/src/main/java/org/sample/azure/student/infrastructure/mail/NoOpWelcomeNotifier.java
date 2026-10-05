package org.sample.azure.student.infrastructure.mail;

import org.sample.azure.student.application.StudentRegisteredEvent;
import org.sample.azure.student.application.WelcomeNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mail.welcome", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpWelcomeNotifier implements WelcomeNotifier {

    private static final Logger log = LoggerFactory.getLogger(NoOpWelcomeNotifier.class);

    @Override
    public void sendWelcome(StudentRegisteredEvent student) {
        log.debug("Welcome email disabled; skipping student id={}", student.id());
    }
}
