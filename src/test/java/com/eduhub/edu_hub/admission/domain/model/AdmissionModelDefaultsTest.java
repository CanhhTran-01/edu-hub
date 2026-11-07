package com.eduhub.edu_hub.admission.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import org.junit.jupiter.api.Test;

class AdmissionModelDefaultsTest {
    @Test
    void buildersKeepInitialWorkflowStates() {
        assertThat(AdmissionPlan.builder().build().getState()).isEqualTo(AdmissionPlanStatus.DRAFT);
        assertThat(AdmissionApplicant.builder().build().getState()).isEqualTo(AdmissionApplicantStatus.VALID);
        assertThat(StudentAdmission.builder().build().getActivityStatus()).isEqualTo(ActivityStatus.STUDYING);
    }
}
