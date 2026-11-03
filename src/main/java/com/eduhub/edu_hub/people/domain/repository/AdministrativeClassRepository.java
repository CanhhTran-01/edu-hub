package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministrativeClassRepository extends JpaRepository<AdministrativeClass, Long> {
    @EntityGraph(attributePaths = "cohort")
    Optional<AdministrativeClass> findByIdAndDeletedFalse(Long id);
}
