package org.sample.azure.student.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sample.azure.student.domain.StudentProfile;
import org.sample.azure.student.infrastructure.persistence.StudentProfileRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentProfileRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private StudentService service;

    @Test
    @DisplayName("AC-01: listStudents pide los registros ordenados por id")
    void listStudentsSortsById() {
        List<StudentProfile> expected = List.of(new StudentProfile("Alice", "alice@example.com", "Biology"));
        when(repository.findAll(Sort.by("id"))).thenReturn(expected);

        assertThat(service.listStudents()).isSameAs(expected);
    }

    @Test
    @DisplayName("AC-06: register guarda el estudiante y devuelve la entidad guardada")
    void registerSavesStudent() {
        when(repository.save(any(StudentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentProfile saved = service.register("Alice Johnson", "alice@example.com", "Biology");

        assertThat(saved.getName()).isEqualTo("Alice Johnson");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getMajor()).isEqualTo("Biology");
    }

    @Test
    @DisplayName("AC-13: register publica el evento de alta para el email de bienvenida")
    void registerPublishesStudentRegisteredEvent() {
        when(repository.save(any(StudentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.register("Alice Johnson", "alice@example.com", "Biology");

        verify(eventPublisher).publishEvent(new StudentRegisteredEvent(null, "Alice Johnson", "alice@example.com"));
    }
}
