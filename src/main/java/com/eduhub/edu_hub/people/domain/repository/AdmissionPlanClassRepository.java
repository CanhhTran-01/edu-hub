package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.AdmissionPlanClass;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionPlanClassRepository extends JpaRepository<AdmissionPlanClass, Long> {
    List<AdmissionPlanClass> findByPlan_IdOrderByPosition(Long planId);

    boolean existsByAdministrativeClass_Id(Long classId);
}
