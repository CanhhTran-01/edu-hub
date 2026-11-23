package com.eduhub.edu_hub.admission.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AdmissionApplicantMergePolicyTest {
    private final AdmissionApplicantMergePolicy mergePolicy =
            new AdmissionApplicantMergePolicy(new AdmissionIdentityPolicy());

    @Test
    void ignoresAnEquivalentSupplementWithoutChangingTheEnrolledApplicant() {
        var enrolled = applicant("HS01", "012345678901", 6);
        enrolled.setState(AdmissionApplicantStatus.ENROLLED);
        enrolled.setStudentId(31L);
        enrolled.setAdministrativeClassId(41L);
        assertThat(mergePolicy.appendApplicants(List.of(enrolled), List.of(applicant("HS01", "012345678901", 6)), 6))
                .containsExactly(enrolled);
        assertThat(enrolled.getStudentId()).isEqualTo(31L);
        assertThat(enrolled.getAdministrativeClassId()).isEqualTo(41L);
        assertThat(enrolled.getState()).isEqualTo(AdmissionApplicantStatus.ENROLLED);
    }

    @ParameterizedTest
    @EnumSource(
            value = AdmissionApplicantStatus.class,
            names = {"VALID", "FAILED", "INVALID", "CONFLICT", "ENROLLED"})
    void neverOverwritesAnExistingRowFromASupplementalFile(AdmissionApplicantStatus oldState) {
        var existing = applicant("HS01", "012345678901", 6);
        existing.setState(oldState);
        existing.setId(11L);
        var incoming = applicant("HS01", "012345678901", 6);
        incoming.setFullName("Trần Văn Bình");
        var combined = mergePolicy.appendApplicants(List.of(existing), List.of(incoming), 6);
        assertThat(combined).containsExactly(existing, incoming);
        assertThat(existing.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(existing.getState()).isEqualTo(oldState);
        assertThat(existing.getId()).isEqualTo(11L);
        assertThat(incoming.getState()).isEqualTo(AdmissionApplicantStatus.CONFLICT);
        assertThat(incoming.getRowNumber()).isEqualTo(7);
    }

    @Test
    void rejectsANewApplicationCodeForACitizenAlreadyEnrolledInThePlan() {
        var enrolled = applicant("HS01", "012345678901", 6);
        enrolled.setState(AdmissionApplicantStatus.ENROLLED);
        var incoming = applicant("HS02", "012345678901", 6);
        assertThat(mergePolicy.appendApplicants(List.of(enrolled), List.of(incoming), 6))
                .containsExactly(enrolled, incoming);
        assertThat(incoming.getState()).isEqualTo(AdmissionApplicantStatus.CONFLICT);
        assertThat(enrolled.getState()).isEqualTo(AdmissionApplicantStatus.ENROLLED);
    }

    @Test
    void preservesExistingPendingApplicantsWhenOnlyNewRowsAreUploaded() {
        var existing = applicant("HS01", "012345678901", 9);
        var incoming = applicant("HS02", "012345678902", 6);
        assertThat(mergePolicy.appendApplicants(List.of(existing), List.of(incoming), 9))
                .containsExactly(existing, incoming);
        assertThat(incoming.getRowNumber()).isEqualTo(10);
        assertThat(incoming.getState()).isEqualTo(AdmissionApplicantStatus.VALID);
    }

    @Test
    void appendsAfterDeletedRowsWithoutReusingTheirNumbers() {
        var existing = applicant("HS01", "012345678901", 9);
        var firstIncoming = applicant("HS02", "012345678902", 6);
        var secondIncoming = applicant("HS03", "012345678903", 8);

        var combined = mergePolicy.appendApplicants(List.of(existing), List.of(firstIncoming, secondIncoming), 17);

        assertThat(combined).containsExactly(existing, firstIncoming, secondIncoming);
        assertThat(existing.getRowNumber()).isEqualTo(9);
        assertThat(firstIncoming.getRowNumber()).isEqualTo(18);
        assertThat(secondIncoming.getRowNumber()).isEqualTo(19);
    }

    @Test
    void acceptsANewApplicantAfterEveryPreviousRowWasSoftDeleted() {
        var incoming = applicant("HS01", "012345678901", 6);

        assertThat(mergePolicy.appendApplicants(List.of(), List.of(incoming), 17))
                .containsExactly(incoming);

        assertThat(incoming.getRowNumber()).isEqualTo(18);
        assertThat(incoming.getState()).isEqualTo(AdmissionApplicantStatus.VALID);
    }

    @Test
    void keepsPhysicalSpreadsheetPositionsOnlyWhenThePlanHasNoStoredRows() {
        var firstIncoming = applicant("HS01", "012345678901", 6);
        var secondIncoming = applicant("HS02", "012345678902", 20);

        mergePolicy.appendApplicants(List.of(), List.of(firstIncoming, secondIncoming), 0);

        assertThat(firstIncoming.getRowNumber()).isEqualTo(6);
        assertThat(secondIncoming.getRowNumber()).isEqualTo(20);
    }

    private AdmissionApplicant applicant(String applicationCode, String citizenId, int rowNumber) {
        return AdmissionApplicant.builder()
                .applicationCode(applicationCode)
                .citizenId(citizenId)
                .fullName("Nguyễn Văn An")
                .entranceScore(new BigDecimal("25.00"))
                .rowNumber(rowNumber)
                .build();
    }
}
