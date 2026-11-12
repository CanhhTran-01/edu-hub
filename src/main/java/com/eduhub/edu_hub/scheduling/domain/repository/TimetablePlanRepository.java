package com.eduhub.edu_hub.scheduling.domain.repository;

import com.eduhub.edu_hub.scheduling.domain.enums.TimetablePlanState;
import com.eduhub.edu_hub.scheduling.domain.model.TimetablePlan;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TimetablePlanRepository extends JpaRepository<TimetablePlan, Long> {
    @Query("select plan.semesterId from TimetablePlan plan where plan.id = :id and plan.deleted = false")
    Optional<Long> findSemesterIdById(@Param("id") Long id);

    Optional<TimetablePlan> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from TimetablePlan plan where plan.id = :id and plan.deleted = false")
    Optional<TimetablePlan> findForUpdateById(@Param("id") Long id);

    Optional<TimetablePlan> findFirstBySemesterIdAndStateAndDeletedFalseOrderByAppliedAtDesc(
            Long semesterId, TimetablePlanState state);

    boolean existsByStateInAndDeletedFalse(Collection<TimetablePlanState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from TimetablePlan plan where plan.state in :states and plan.expiresAt < :now "
            + "and plan.deleted = false order by plan.id")
    List<TimetablePlan> findExpiredForUpdate(
            @Param("states") Collection<TimetablePlanState> states, @Param("now") LocalDateTime now);
}
