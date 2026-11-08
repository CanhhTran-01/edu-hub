package com.eduhub.edu_hub.admission.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdmissionApplicantMergePolicyTest {
    private final AdmissionApplicantMergePolicy mergePolicy =
            new AdmissionApplicantMergePolicy(new AdmissionIdentityPolicy());

    @Test
    void ignoresAnEquivalentReimportWithoutChangingTheEnrolledApplicant() {
        AdmissionApplicant enrolledApplicant = applicant("HS01", "Nguyễn Văn An", 6);
        enrolledApplicant.setState(AdmissionApplicantStatus.ENROLLED);
        enrolledApplicant.setStudentId(31L);
        enrolledApplicant.setAdministrativeClassId(41L);
        AdmissionApplicant incomingApplicant = applicant("HS01", "  NGUYỄN VĂN AN  ", 8);
        incomingApplicant.setState(AdmissionApplicantStatus.EXISTING);
        var existingApplicants = new ArrayList<>(List.of(enrolledApplicant));

        assertThat(mergePolicy.mergePreservingEnrolledApplicants(existingApplicants, List.of(incomingApplicant)))
                .isSameAs(existingApplicants)
                .containsExactly(enrolledApplicant);
        assertThat(enrolledApplicant.getState()).isEqualTo(AdmissionApplicantStatus.ENROLLED);
        assertThat(enrolledApplicant.getStudentId()).isEqualTo(31L);
        assertThat(enrolledApplicant.getAdministrativeClassId()).isEqualTo(41L);
        assertThat(enrolledApplicant.getFullName()).isEqualTo("Nguyễn Văn An");
    }

    @Test
    void appendsAConflictingRowInsteadOfOverwritingAnEnrolledApplicant() {
        AdmissionApplicant enrolledApplicant = applicant("HS01", "Nguyễn Văn An", 6);
        enrolledApplicant.setState(AdmissionApplicantStatus.ENROLLED);
        AdmissionApplicant incomingApplicant = applicant("HS01", "Trần Văn Bình", 6);
        var existingApplicants = new ArrayList<>(List.of(enrolledApplicant));

        assertThat(mergePolicy.mergePreservingEnrolledApplicants(existingApplicants, List.of(incomingApplicant)))
                .containsExactly(enrolledApplicant, incomingApplicant);
        assertThat(enrolledApplicant.getState()).isEqualTo(AdmissionApplicantStatus.ENROLLED);
        assertThat(enrolledApplicant.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(incomingApplicant.getRowNumber()).isEqualTo(7);
        assertThat(incomingApplicant.getState()).isEqualTo(AdmissionApplicantStatus.CONFLICT);
        assertThat(incomingApplicant.getErrorMessage()).isEqualTo("Không ghi đè hồ sơ đã nhập học thành công");
    }

    @Test
    void updatesTheExistingInstanceWhilePreservingItsIdentityAndAuditFields() {
        AdmissionPlan originalPlan = AdmissionPlan.builder().build();
        AdmissionApplicant existingApplicant = applicant("HS01", "Nguyễn Văn An", 6);
        existingApplicant.setId(11L);
        existingApplicant.setPlan(originalPlan);
        existingApplicant.setCreatedAt(LocalDateTime.of(2026, 11, 8, 10, 0));
        existingApplicant.setCreatedBy("operator");
        existingApplicant.setDeleted(true);
        AdmissionApplicant incomingApplicant = applicant("HS01", "Nguyễn Văn An đã sửa", 20);
        incomingApplicant.setId(99L);
        var existingApplicants = new ArrayList<>(List.of(existingApplicant));

        mergePolicy.mergePreservingEnrolledApplicants(existingApplicants, List.of(incomingApplicant));

        assertThat(existingApplicants).singleElement().isSameAs(existingApplicant);
        assertThat(existingApplicant.getFullName()).isEqualTo("Nguyễn Văn An đã sửa");
        assertThat(existingApplicant.getId()).isEqualTo(11L);
        assertThat(existingApplicant.getPlan()).isSameAs(originalPlan);
        assertThat(existingApplicant.getRowNumber()).isEqualTo(6);
        assertThat(existingApplicant.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 8, 10, 0));
        assertThat(existingApplicant.getCreatedBy()).isEqualTo("operator");
        assertThat(existingApplicant.isDeleted()).isTrue();
    }

    private AdmissionApplicant applicant(String applicationCode, String fullName, int rowNumber) {
        return AdmissionApplicant.builder()
                .applicationCode(applicationCode)
                .citizenId("012345678901")
                .fullName(fullName)
                .entranceScore(new BigDecimal("25.00"))
                .rowNumber(rowNumber)
                .build();
    }
}
