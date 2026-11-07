package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.Cohort;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CohortRepository extends JpaRepository<Cohort, Long> {
    Optional<Cohort> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cohort c where c.id = :id and c.deleted = false")
    Optional<Cohort> findForUpdate(@Param("id") Long id);
}
