package org.sample.azure.student.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WelcomeNotificationListener {

    private final WelcomeNotifier welcomeNotifier;

    public WelcomeNotificationListener(WelcomeNotifier welcomeNotifier) {
        this.welcomeNotifier = welcomeNotifier;
    }

    @TransactionalEventListener
    public void onStudentRegistered(StudentRegisteredEvent event) {
        welcomeNotifier.sendWelcome(event);
    }
}
