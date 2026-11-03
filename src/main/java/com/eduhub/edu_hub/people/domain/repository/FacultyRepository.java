package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.Faculty;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    Optional<Faculty> findByIdAndDeletedFalseAndActiveTrue(Long id);
}
