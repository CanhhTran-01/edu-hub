package com.eduhub.edu_hub.admission.domain.repository;

import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionApplicantRepository extends JpaRepository<AdmissionApplicant, Long> {
    List<AdmissionApplicant> findByPlan_IdAndDeletedFalseOrderByRowNumber(Long planId);

    Optional<AdmissionApplicant> findByIdAndDeletedFalse(Long applicantId);

    // Unique (plan_id, row_number) vẫn giữ dòng đã xóa mềm, nên số mới phải nối sau toàn bộ lịch sử.
    @Query("select coalesce(max(a.rowNumber), 0) from AdmissionApplicant a where a.plan.id = :planId")
    int findMaximumRowNumberByPlanIdIncludingDeleted(@Param("planId") Long planId);

    void deleteByPlan_Id(Long planId);
}
