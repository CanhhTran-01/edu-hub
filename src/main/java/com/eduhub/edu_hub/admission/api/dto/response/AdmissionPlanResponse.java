package com.eduhub.edu_hub.admission.api.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

@Builder
public record AdmissionPlanResponse(
        Long id,
        long revision,
        Long majorId,
        Long cohortId,
        int totalStudents,
        AdmissionCapacityResponse capacity,
        String state,
        Integer selectedClassCount,
        List<AdmissionScenarioResponse> scenarios,
        List<AdmissionClassPreviewResponse> classes,
        List<AdmissionApplicantResponse> applicants,
        BigDecimal averageScoreDifference,
        List<String> warnings) {}
