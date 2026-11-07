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
    public ApplicantResult applicant(AdmissionApplicant row, AdmissionStudentInfo student) {
        return ApplicantResult.builder()
                .id(row.getId())
                .rowNumber(row.getRowNumber())
                .applicationCode(row.getApplicationCode())
                .citizenId(row.getCitizenId())
                .fullName(row.getFullName())
                .dateOfBirth(row.getDateOfBirth())
                .gender(row.getGender())
                .email(row.getEmail())
                .phone(row.getPhone())
                .entranceScore(row.getEntranceScore())
                .state(row.getState().name())
                .error(row.getErrorMessage())
                .assignedClassId(row.getAdministrativeClassId())
                .studentId(row.getStudentId())
                .studentCode(student == null ? null : student.getStudentCode())
                .reusingAccount(row.isReuseAccount())
                .warnings(row.getWarningMessage() == null ? List.of() : List.of(row.getWarningMessage()))
                .build();
    }

    public PlanResult plan(
            AdmissionPlan plan,
            List<ClassResult> classes,
            List<ApplicantResult> applicants,
            List<ScenarioResult> scenarios,
            BigDecimal difference,
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
                .classes(List.copyOf(classes))
                .applicants(List.copyOf(applicants))
                .scenarios(scenarios)
                .averageScoreDifference(difference)
                .warnings(List.copyOf(warnings))
                .build();
    }
}
