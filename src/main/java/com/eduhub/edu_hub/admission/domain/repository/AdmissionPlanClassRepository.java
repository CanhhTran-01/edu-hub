package com.eduhub.edu_hub.admission.domain.repository;

import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionPlanClassRepository extends JpaRepository<AdmissionPlanClass, Long> {
    List<AdmissionPlanClass> findByPlan_IdOrderByPosition(Long planId);

    boolean existsByAdministrativeClassId(Long classId);
}
