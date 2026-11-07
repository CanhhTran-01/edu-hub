package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.StudentAdmission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAdmissionRepository extends JpaRepository<StudentAdmission, Long> {
    Optional<StudentAdmission> findByApplicationCode(String applicationCode);

    List<StudentAdmission> findByApplicationCodeIn(List<String> codes);

    long countByStudent_IdAndAdministrativeClass_IdAndDeletedFalse(Long studentId, Long classId);
}
