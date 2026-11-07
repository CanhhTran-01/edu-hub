package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.AdmissionPlan;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AdmissionPlanRepository extends JpaRepository<AdmissionPlan, Long> {
    Optional<AdmissionPlan> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AdmissionPlan p where p.id = :id and p.deleted = false")
    Optional<AdmissionPlan> findForUpdate(@Param("id") Long id);
}
