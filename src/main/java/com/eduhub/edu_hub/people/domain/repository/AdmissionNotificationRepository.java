package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.AdmissionNotification;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AdmissionNotificationRepository extends JpaRepository<AdmissionNotification, Long> {
    @Query("select n.id from AdmissionNotification n where n.deleted = false "
            + "and n.state in :states and n.failureCount < :maxFailures and n.nextAttemptAt <= :now order by n.id")
    List<Long> findDue(
            @Param("states") List<AdmissionNotification.State> states,
            @Param("now") LocalDateTime now,
            @Param("maxFailures") int maxFailures,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from AdmissionNotification n where n.id = :id and n.deleted = false")
    Optional<AdmissionNotification> findForUpdate(@Param("id") Long id);

    List<AdmissionNotification> findByAdmission_Plan_IdOrderById(Long planId);
}
