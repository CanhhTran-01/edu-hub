package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.Cohort;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CohortRepository extends JpaRepository<Cohort, Long> {
    // Khóa tự loại trừ giữa các transaction tạo; vẫn cho phép đọc danh mục và khóa hàng phục vụ nhập học.
    @Modifying
    @Query(value = "LOCK TABLE cohorts IN SHARE ROW EXCLUSIVE MODE", nativeQuery = true)
    void lockForCohortCreation();

    @Query("select coalesce(max(cohort.codeSequence), 0) from Cohort cohort")
    int findLastIssuedCodeSequence();

    boolean existsByStartYear(Integer startYear);

    @Query("select count(cohort) > 0 from Cohort cohort where lower(trim(cohort.cohortCode)) = lower(:cohortCode)")
    boolean existsByNormalizedCohortCode(@Param("cohortCode") String cohortCode);

    List<Cohort> findAllByDeletedFalseOrderByIdDesc();

    List<Cohort> findAllByIdInAndDeletedFalseOrderByIdAsc(Set<Long> cohortIds);

    Optional<Cohort> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cohort c where c.id = :id and c.deleted = false")
    Optional<Cohort> findForUpdate(@Param("id") Long id);
}
