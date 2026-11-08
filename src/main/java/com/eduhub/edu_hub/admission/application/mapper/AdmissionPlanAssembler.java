package com.eduhub.edu_hub.admission.application.mapper;

import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ApplicantResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.CapacityResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ClassResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.PlanResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ScenarioResult;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentInfo;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdmissionPlanAssembler {
    public ApplicantResult toApplicantResult(AdmissionApplicant applicant, AdmissionStudentInfo student) {
        return ApplicantResult.builder()
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

    public PlanResult toPlanResult(
            AdmissionPlan plan,
            List<ClassResult> classResults,
            List<ApplicantResult> applicantResults,
            List<ScenarioResult> scenarioResults,
            BigDecimal averageScoreDifference,
            List<String> warnings) {
        return PlanResult.builder()
                .id(plan.getId())
                .revision(plan.getRevision())
                .majorId(plan.getMajorId())
                .cohortId(plan.getCohortId())
                .totalStudents(plan.getTotalStudents())
                .state(plan.getState().name())
                .selectedClassCount(plan.getSelectedClassCount())
                .capacity(CapacityResult.builder()
                        .minimum(plan.getMinCapacity())
                        .maximum(plan.getMaxCapacity())
                        .reserve(plan.getReserveCapacity())
                        .build())
                .classes(List.copyOf(classResults))
                .applicants(List.copyOf(applicantResults))
                .scenarios(scenarioResults)
                .averageScoreDifference(averageScoreDifference)
                .warnings(List.copyOf(warnings))
                .build();
    }
}
