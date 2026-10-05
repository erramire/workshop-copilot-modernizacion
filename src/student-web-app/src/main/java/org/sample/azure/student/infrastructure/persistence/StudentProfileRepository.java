package org.sample.azure.student.infrastructure.persistence;

import org.sample.azure.student.domain.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Integer> {
}
