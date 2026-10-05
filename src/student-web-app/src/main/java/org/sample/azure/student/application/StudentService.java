package org.sample.azure.student.application;

import java.util.List;

import org.sample.azure.student.domain.StudentProfile;
import org.sample.azure.student.infrastructure.persistence.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final StudentProfileRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public StudentService(StudentProfileRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<StudentProfile> listStudents() {
        return repository.findAll(Sort.by("id"));
    }

    // El email de bienvenida se envía tras el commit, en WelcomeNotificationListener (ADR-011)
    @Transactional
    public StudentProfile register(String name, String email, String major) {
        StudentProfile saved = repository.save(new StudentProfile(name, email, major));
        log.info("Student registered with id={}", saved.getId());
        eventPublisher.publishEvent(new StudentRegisteredEvent(saved.getId(), saved.getName(), saved.getEmail()));
        return saved;
    }
}
