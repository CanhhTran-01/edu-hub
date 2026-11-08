package com.eduhub.edu_hub.notification.domain.repository;

import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedAdmissionNotification;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface QueuedAdmissionNotificationRepository extends JpaRepository<QueuedAdmissionNotification, Long> {
    @Query("select n.id from QueuedAdmissionNotification n where n.deleted = false "
            + "and n.state in :states and n.failureCount < :maxFailures and n.nextAttemptAt <= :now order by n.id")
    List<Long> findDueNotificationIds(
            @Param("states") List<NotificationStatus> deliveryStatuses,
            @Param("now") LocalDateTime dueAt,
            @Param("maxFailures") int maxFailureCount,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from QueuedAdmissionNotification n where n.id = :id and n.deleted = false")
    Optional<QueuedAdmissionNotification> findByIdForUpdate(@Param("id") Long notificationId);

    List<QueuedAdmissionNotification> findByAdmissionIdInAndDeletedFalseOrderById(List<Long> admissionIds);
}
