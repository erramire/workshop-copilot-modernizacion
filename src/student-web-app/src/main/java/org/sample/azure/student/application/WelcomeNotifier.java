package org.sample.azure.student.application;

public interface WelcomeNotifier {

    void sendWelcome(StudentRegisteredEvent student);
}
