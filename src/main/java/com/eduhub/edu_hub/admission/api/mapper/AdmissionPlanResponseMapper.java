package com.eduhub.edu_hub.admission.api.mapper;

import com.eduhub.edu_hub.admission.api.dto.response.AdmissionApplicantResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionCapacityResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionClassPreviewResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionScenarioResponse;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentInfo;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdmissionPlanResponseMapper {
    public AdmissionApplicantResponse toApplicantResponse(AdmissionApplicant applicant, AdmissionStudentInfo student) {
        return AdmissionApplicantResponse.builder()
                .id(applicant.getId())
                .rowNumber(applicant.getRowNumber())
                .applicationCode(applicant.getApplicationCode())
                .citizenId(applicant.getCitizenId())
                .fullName(applicant.getFullName())
                .dateOfBirth(applicant.getDateOfBirth())
                .gender(applicant.getGender())
                .email(applicant.getEmail())
                .phone(applicant.getPhone())
                .entranceScore(applicant.getEntranceScore())
                .state(applicant.getState().name())
                .error(applicant.getErrorMessage())
                .assignedClassId(applicant.getAdministrativeClassId())
                .studentId(applicant.getStudentId())
                .studentCode(student == null ? null : student.getStudentCode())
                .reusingAccount(applicant.isReuseAccount())
                .warnings(applicant.getWarningMessage() == null ? List.of() : List.of(applicant.getWarningMessage()))
                .build();
    }

    public AdmissionPlanResponse toPlanResponse(
            AdmissionPlan plan,
            List<AdmissionClassPreviewResponse> classResponses,
            List<AdmissionApplicantResponse> applicantResponses,
            List<AdmissionScenarioResponse> scenarioResponses,
            BigDecimal averageScoreDifference,
            List<String> warnings) {
        return AdmissionPlanResponse.builder()
                .id(plan.getId())
                .revision(plan.getRevision())
                .majorId(plan.getMajorId())
                .cohortId(plan.getCohortId())
                .totalStudents(plan.getTotalStudents())
                .state(plan.getState().name())
                .selectedClassCount(plan.getSelectedClassCount())
                .capacity(AdmissionCapacityResponse.builder()
                        .minimum(plan.getMinCapacity())
                        .maximum(plan.getMaxCapacity())
                        .reserve(plan.getReserveCapacity())
                        .build())
                .classes(List.copyOf(classResponses))
                .applicants(List.copyOf(applicantResponses))
                .scenarios(scenarioResponses)
                .averageScoreDifference(averageScoreDifference)
                .warnings(List.copyOf(warnings))
                .build();
    }
}
