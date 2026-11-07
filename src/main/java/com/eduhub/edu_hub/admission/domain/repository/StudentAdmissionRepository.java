package com.eduhub.edu_hub.admission.domain.repository;

import com.eduhub.edu_hub.admission.domain.model.StudentAdmission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentAdmissionRepository extends JpaRepository<StudentAdmission, Long> {
    Optional<StudentAdmission> findByApplicationCode(String applicationCode);

    List<StudentAdmission> findByApplicationCodeIn(List<String> codes);

    long countByStudentIdAndAdministrativeClassIdAndDeletedFalse(Long studentId, Long classId);

    @Query("select a.id from StudentAdmission a where a.plan.id = :planId and a.deleted = false order by a.id")
    List<Long> findIdsByPlanId(@Param("planId") Long planId);
}
