package org.sample.azure.student.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sample.azure.student.domain.StudentProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StudentProfileRepositoryTest {

    @Autowired
    private StudentProfileRepository repository;

    @Test
    @DisplayName("Al guardar, la base de datos genera el id")
    void saveGeneratesId() {
        StudentProfile saved = repository.save(new StudentProfile("Alice Johnson", "alice@example.com", "Biology"));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("AC-01: el listado se ordena por id ascendente")
    void findAllSortedById() {
        StudentProfile first = repository.save(new StudentProfile("Zoe", "zoe@example.com", "Art"));
        StudentProfile second = repository.save(new StudentProfile("Adam", "adam@example.com", "Math"));

        List<Integer> ids = repository.findAll(Sort.by("id")).stream().map(StudentProfile::getId).toList();

        assertThat(ids).isSorted().containsSubsequence(first.getId(), second.getId());
    }

    @Test
    @DisplayName("AC-08: se permiten emails duplicados")
    void allowsDuplicateEmails() {
        repository.save(new StudentProfile("Alice", "dup@example.com", "Biology"));
        repository.save(new StudentProfile("Alice B.", "dup@example.com", "Physics"));

        assertThat(repository.findAll())
                .filteredOn(student -> "dup@example.com".equals(student.getEmail()))
                .hasSize(2);
    }
}
