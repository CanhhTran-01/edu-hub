package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.AdmissionApplicant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionApplicantRepository extends JpaRepository<AdmissionApplicant, Long> {
    List<AdmissionApplicant> findByPlan_IdOrderByRowNumber(Long planId);
    void deleteByPlan_Id(Long planId);
}
