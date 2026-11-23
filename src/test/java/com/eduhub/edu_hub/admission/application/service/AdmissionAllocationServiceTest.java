package com.eduhub.edu_hub.admission.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.domain.enums.*;
import com.eduhub.edu_hub.admission.domain.model.*;
import com.eduhub.edu_hub.admission.domain.policy.*;
import com.eduhub.edu_hub.admission.domain.repository.*;
import com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AdmissionAllocationServiceTest {
    private final AdmissionClassPreparationService preparationService = mock(AdmissionClassPreparationService.class);
    private final AdmissionAllocationService service = new AdmissionAllocationService(
            mock(AdmissionPlanLockService.class),
            preparationService,
            mock(AdmissionPlanClassRepository.class),
            mock(AdmissionApplicantRepository.class),
            new SerpentineClassAllocator(),
            mock(AdmissionPlanQueryService.class),
            SystemConfigTestData.configuredService());

    @Test
    void allocatesOnlyPendingApplicantsAndPreservesEnrolledClassAndStudentIds() {
        var plan = AdmissionPlan.builder().revision(5).build();
        var enrolled = applicant(AdmissionApplicantStatus.ENROLLED);
        enrolled.setAdministrativeClassId(10L);
        enrolled.setStudentId(31L);
        var failed = applicant(AdmissionApplicantStatus.FAILED);
        var invalid = applicant(AdmissionApplicantStatus.INVALID);
        var applicants = new ArrayList<>(List.of(enrolled, failed, invalid));
        when(preparationService.prepareClassesAutomatically(plan, applicants))
                .thenReturn(List.of(
                        AdmissionPlanClass.builder()
                                .administrativeClassId(10L)
                                .targetSize(1)
                                .build(),
                        AdmissionPlanClass.builder()
                                .administrativeClassId(20L)
                                .targetSize(1)
                                .build()));

        service.prepareAndAllocateApplicants(plan, applicants);

        assertThat(enrolled.getAdministrativeClassId()).isEqualTo(10L);
        assertThat(enrolled.getStudentId()).isEqualTo(31L);
        assertThat(enrolled.getState()).isEqualTo(AdmissionApplicantStatus.ENROLLED);
        assertThat(failed.getAdministrativeClassId()).isEqualTo(20L);
        assertThat(invalid.getAdministrativeClassId()).isNull();
        assertThat(plan.getTotalStudents()).isEqualTo(2);
        assertThat(plan.getState()).isEqualTo(AdmissionPlanStatus.PREVIEWED);
        assertThat(plan.getRevision()).isEqualTo(5);
    }

    @Test
    void exposesANonConfirmablePlanWhenNoAllocationFitsTheIdentifierLimit() {
        var plan = AdmissionPlan.builder().build();
        var applicant = applicant(AdmissionApplicantStatus.VALID);
        applicant.setAdministrativeClassId(10L);
        service.prepareAndAllocateApplicants(plan, List.of(applicant));
        assertThat(plan.getState()).isEqualTo(AdmissionPlanStatus.DRAFT);
        assertThat(plan.getTotalStudents()).isEqualTo(1);
        assertThat(applicant.getAdministrativeClassId()).isNull();
        assertThat(AdmissionPlanPolicy.findConfirmationBlockReasons(plan, List.of(applicant), List.of()))
                .isNotEmpty();
    }

    @Test
    void marksAPlanWithOnlyEnrolledApplicantsCompleteWithoutCreatingClasses() {
        var plan = AdmissionPlan.builder().build();
        var enrolled = applicant(AdmissionApplicantStatus.ENROLLED);
        enrolled.setAdministrativeClassId(10L);
        service.prepareAndAllocateApplicants(plan, List.of(enrolled));
        assertThat(plan.getState()).isEqualTo(AdmissionPlanStatus.CONFIRMED);
        assertThat(plan.getTotalStudents()).isEqualTo(1);
        assertThat(enrolled.getAdministrativeClassId()).isEqualTo(10L);
        verifyNoInteractions(preparationService);
    }

    @Test
    void balancesAPopulationWithinThePreparedTargets() {
        var plan = AdmissionPlan.builder().build();
        List<AdmissionApplicant> applicants = IntStream.range(0, 83)
                .mapToObj(index -> {
                    var applicant = applicant(AdmissionApplicantStatus.VALID);
                    applicant.setFullName("Thí sinh " + index);
                    return applicant;
                })
                .toList();
        when(preparationService.prepareClassesAutomatically(plan, applicants))
                .thenReturn(List.of(
                        AdmissionPlanClass.builder()
                                .administrativeClassId(10L)
                                .targetSize(42)
                                .build(),
                        AdmissionPlanClass.builder()
                                .administrativeClassId(20L)
                                .targetSize(41)
                                .build()));
        service.prepareAndAllocateApplicants(plan, applicants);
        assertThat(applicants.stream()
                        .filter(applicant -> applicant.getAdministrativeClassId() == 10L)
                        .count())
                .isEqualTo(42);
        assertThat(applicants.stream()
                        .filter(applicant -> applicant.getAdministrativeClassId() == 20L)
                        .count())
                .isEqualTo(41);
        assertThat(plan.getTotalStudents()).isEqualTo(83);
    }

    private AdmissionApplicant applicant(AdmissionApplicantStatus state) {
        return AdmissionApplicant.builder()
                .state(state)
                .fullName("Nguyễn Văn An")
                .entranceScore(new BigDecimal("25.00"))
                .build();
    }
}
