package com.eduhub.edu_hub.admission.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.api.dto.request.DeleteAdmissionApplicantRequest;
import com.eduhub.edu_hub.admission.api.dto.request.UpdateAdmissionApplicantRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.exception.*;
import com.eduhub.edu_hub.admission.domain.enums.*;
import com.eduhub.edu_hub.admission.domain.model.*;
import com.eduhub.edu_hub.admission.domain.policy.*;
import com.eduhub.edu_hub.admission.domain.repository.*;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.validation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AdmissionApplicantCorrectionServiceTest {
    private final AdmissionPlanLockService lockService = mock(AdmissionPlanLockService.class);
    private final AdmissionApplicantRepository repository = mock(AdmissionApplicantRepository.class);
    private final AdmissionAllocationService allocationService = mock(AdmissionAllocationService.class);
    private final AdmissionPlanQueryService planQueryService = mock(AdmissionPlanQueryService.class);
    private final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
    private final AdmissionIdentityPolicy identityPolicy = new AdmissionIdentityPolicy();
    private final AdmissionApplicantCorrectionService service = new AdmissionApplicantCorrectionService(
            lockService,
            repository,
            new AdmissionApplicantValidationPolicy(validators.getValidator()),
            new AdmissionApplicantClassificationService(
                    mock(StudentAdmissionRepository.class),
                    mock(PeopleFacade.Admission.class),
                    mock(IdentityFacade.class),
                    identityPolicy),
            identityPolicy,
            allocationService,
            planQueryService,
            Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
    private final AdmissionPlan plan = AdmissionPlan.builder().revision(5).build();
    private final AdmissionApplicant target = applicant(11L, "HS01", "012345678901", AdmissionApplicantStatus.INVALID);
    private List<AdmissionApplicant> applicants;

    @BeforeEach
    void prepareApplicant() {
        plan.setId(7L);
        target.setPlan(plan);
        target.setEmail("invalid-email");
        applicants = new ArrayList<>(List.of(target));
        when(lockService.lockPlanAtRevision(7L, 5)).thenReturn(plan);
        when(repository.findByPlan_IdAndDeletedFalseOrderByRowNumber(7L)).thenAnswer(invocation -> applicants);
    }

    @AfterEach
    void closeValidators() {
        validators.close();
    }

    @Test
    void fixesTheManagedApplicantAndReclassifiesRelatedDuplicates() {
        target.setErrorMessage("Lỗi parse cũ");
        target.setDateOfBirth(LocalDate.of(2000, 1, 1));
        var duplicate = applicant(12L, "HS01", "012345678901", AdmissionApplicantStatus.CONFLICT);
        duplicate.setDateOfBirth(LocalDate.of(2000, 1, 1));
        applicants.add(duplicate);
        var request = request();
        request.setEmail(null);

        service.updateApplicant(7L, 11L, request);

        assertThat(target.getState()).isEqualTo(AdmissionApplicantStatus.VALID);
        assertThat(target.getEmail()).isNull();
        assertThat(target.getDateOfBirth()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(target.getErrorMessage()).isNull();
        assertThat(target.getId()).isEqualTo(11L);
        assertThat(target.getPlan()).isSameAs(plan);
        assertThat(duplicate.getState()).isEqualTo(AdmissionApplicantStatus.EXISTING);
        assertThat(plan.getRevision()).isEqualTo(6);
        verify(allocationService).prepareAndAllocateApplicants(plan, applicants);
    }

    @Test
    void rejectsNullMandatoryIdentityBeforeMutatingTheManagedApplicant() {
        var request = request();
        request.setCitizenId(null);
        request.setEmail(null);
        assertThatThrownBy(() -> service.updateApplicant(7L, 11L, request))
                .isInstanceOf(AdmissionApplicantException.class);
        assertThat(target.getCitizenId()).isEqualTo("012345678901");
        assertThat(target.getEmail()).isEqualTo("invalid-email");
        assertThat(plan.getRevision()).isEqualTo(5);
        verifyNoInteractions(allocationService);
    }

    @Test
    void preservesEnrolledApplicantsAndMarksOnlyTheCorrectedConflictingRow() {
        var enrolled = applicant(12L, "HS02", "012345678902", AdmissionApplicantStatus.ENROLLED);
        enrolled.setStudentId(31L);
        enrolled.setAdministrativeClassId(41L);
        applicants.add(enrolled);
        var request = request();
        request.setCitizenId("012345678902");
        request.setEmail(null);

        service.updateApplicant(7L, 11L, request);

        assertThat(target.getState()).isEqualTo(AdmissionApplicantStatus.CONFLICT);
        assertThat(enrolled.getState()).isEqualTo(AdmissionApplicantStatus.ENROLLED);
        assertThat(enrolled.getStudentId()).isEqualTo(31L);
        assertThat(enrolled.getAdministrativeClassId()).isEqualTo(41L);
    }

    @Test
    void cannotEditAnEnrolledApplicant() {
        target.setState(AdmissionApplicantStatus.ENROLLED);
        assertThatThrownBy(() -> service.updateApplicant(7L, 11L, request()))
                .isInstanceOf(AdmissionPlanException.class);
        verifyNoInteractions(allocationService);
    }

    @Test
    void cannotEditWhileConfirmationIsRunning() {
        plan.setState(AdmissionPlanStatus.CONFIRMING);
        assertThatThrownBy(() -> service.updateApplicant(7L, 11L, request()))
                .isInstanceOf(AdmissionPlanException.class);
        verifyNoInteractions(repository, allocationService);
    }

    @Test
    void softDeletesTheManagedInvalidRowAndKeepsOtherInvalidFailedAndEnrolledApplicants() {
        target.setErrorMessage("Email không hợp lệ");
        var otherInvalid = applicant(12L, "HS02", "012345678902", AdmissionApplicantStatus.INVALID);
        otherInvalid.setErrorMessage("Không đọc được ngày sinh");
        var failed = applicant(13L, "HS03", "012345678903", AdmissionApplicantStatus.FAILED);
        failed.setErrorMessage("Không thể lưu hồ sơ; có thể thử lại");
        var enrolled = applicant(14L, "HS04", "012345678904", AdmissionApplicantStatus.ENROLLED);
        enrolled.setStudentId(31L);
        enrolled.setAdministrativeClassId(41L);
        applicants.addAll(List.of(otherInvalid, failed, enrolled));
        var expectedResponse =
                AdmissionPlanResponse.builder().id(7L).revision(6).build();
        when(planQueryService.buildPlanResponse(plan)).thenReturn(expectedResponse);

        var response = service.deleteApplicant(7L, 11L, new DeleteAdmissionApplicantRequest(5L));

        assertThat(response).isSameAs(expectedResponse);
        assertThat(target.isDeleted()).isTrue();
        assertThat(target.getState()).isEqualTo(AdmissionApplicantStatus.INVALID);
        assertThat(target.getEmail()).isEqualTo("invalid-email");
        assertThat(target.getErrorMessage()).isEqualTo("Email không hợp lệ");
        assertThat(otherInvalid.getState()).isEqualTo(AdmissionApplicantStatus.INVALID);
        assertThat(otherInvalid.getErrorMessage()).isEqualTo("Không đọc được ngày sinh");
        assertThat(failed.getState()).isEqualTo(AdmissionApplicantStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("Không thể lưu hồ sơ; có thể thử lại");
        assertThat(enrolled.getState()).isEqualTo(AdmissionApplicantStatus.ENROLLED);
        assertThat(enrolled.getStudentId()).isEqualTo(31L);
        assertThat(enrolled.getAdministrativeClassId()).isEqualTo(41L);
        assertThat(plan.getRevision()).isEqualTo(6);
        verify(allocationService).prepareAndAllocateApplicants(plan, List.of(otherInvalid, failed, enrolled));
        verify(repository).flush();
        verify(repository, never()).delete(any(AdmissionApplicant.class));
    }

    @Test
    void reclassifiesTheRemainingConflictAfterRemovingTheContradictingRow() {
        target.setState(AdmissionApplicantStatus.CONFLICT);
        target.setEmail(null);
        var remaining = applicant(12L, "HS01", "012345678901", AdmissionApplicantStatus.CONFLICT);
        remaining.setFullName("Trần Văn Bình");
        remaining.setErrorMessage("Cùng mã hồ sơ, khác dữ liệu trong file");
        applicants.add(remaining);

        service.deleteApplicant(7L, 11L, new DeleteAdmissionApplicantRequest(5L));

        assertThat(target.isDeleted()).isTrue();
        assertThat(target.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(remaining.getState()).isEqualTo(AdmissionApplicantStatus.VALID);
        assertThat(remaining.getErrorMessage()).isNull();
        verify(allocationService).prepareAndAllocateApplicants(plan, List.of(remaining));
    }

    @Test
    void canRemoveTheOnlyRowAndReallocateAnEmptyPlan() {
        service.deleteApplicant(7L, 11L, new DeleteAdmissionApplicantRequest(5L));

        assertThat(target.isDeleted()).isTrue();
        assertThat(plan.getRevision()).isEqualTo(6);
        verify(allocationService).prepareAndAllocateApplicants(plan, List.of());
    }

    @ParameterizedTest
    @EnumSource(
            value = AdmissionApplicantStatus.class,
            names = {"VALID", "FAILED", "EXISTING", "ENROLLED"})
    void refusesToRemoveApplicantsOutsideTheCorrectableStates(AdmissionApplicantStatus state) {
        target.setState(state);

        assertThatThrownBy(() -> service.deleteApplicant(7L, 11L, new DeleteAdmissionApplicantRequest(5L)))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_INVALID_STATE));

        assertThat(target.isDeleted()).isFalse();
        assertThat(plan.getRevision()).isEqualTo(5);
        verifyNoInteractions(allocationService, planQueryService);
        verify(repository, never()).flush();
    }

    @Test
    void cannotRemoveApplicantsWhileConfirmationIsRunning() {
        plan.setState(AdmissionPlanStatus.CONFIRMING);

        assertThatThrownBy(() -> service.deleteApplicant(7L, 11L, new DeleteAdmissionApplicantRequest(5L)))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_INVALID_STATE));

        assertThat(target.isDeleted()).isFalse();
        verifyNoInteractions(repository, allocationService, planQueryService);
    }

    @Test
    void refusesRemovalWhenThePlanRevisionIsStale() {
        when(lockService.lockPlanAtRevision(7L, 4))
                .thenThrow(new AdmissionPlanException(ErrorCode.ADMISSION_STALE_PLAN));

        assertThatThrownBy(() -> service.deleteApplicant(7L, 11L, new DeleteAdmissionApplicantRequest(4L)))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_STALE_PLAN));

        assertThat(target.isDeleted()).isFalse();
        verifyNoInteractions(repository, allocationService, planQueryService);
    }

    @Test
    void rejectsAnApplicantOutsideThePlan() {
        assertThatThrownBy(() -> service.deleteApplicant(7L, 99L, new DeleteAdmissionApplicantRequest(5L)))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_APPLICANT_NOT_FOUND));

        assertThat(target.isDeleted()).isFalse();
        verifyNoInteractions(allocationService, planQueryService);
    }

    @Test
    void cannotEditOrRemoveAnAlreadyDeletedApplicant() {
        target.setDeleted(true);
        when(repository.findByPlan_IdAndDeletedFalseOrderByRowNumber(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.deleteApplicant(7L, 11L, new DeleteAdmissionApplicantRequest(5L)))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_APPLICANT_NOT_FOUND));
        assertThatThrownBy(() -> service.updateApplicant(7L, 11L, request()))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_APPLICANT_NOT_FOUND));

        assertThat(plan.getRevision()).isEqualTo(5);
        verifyNoInteractions(allocationService, planQueryService);
    }

    @Test
    void rejectsANegativeRevisionBeforeLockingThePlan() {
        assertThatThrownBy(() -> service.deleteApplicant(7L, 11L, new DeleteAdmissionApplicantRequest(-1L)))
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(lockService, repository, allocationService, planQueryService);
    }

    private UpdateAdmissionApplicantRequest request() {
        var request = new UpdateAdmissionApplicantRequest();
        request.setExpectedRevision(5L);
        return request;
    }

    private AdmissionApplicant applicant(
            Long applicantId, String code, String citizenId, AdmissionApplicantStatus state) {
        var applicant = AdmissionApplicant.builder()
                .applicationCode(code)
                .citizenId(citizenId)
                .fullName("Nguyễn Văn An")
                .entranceScore(new BigDecimal("25.00"))
                .state(state)
                .rowNumber(6)
                .build();
        applicant.setId(applicantId);
        return applicant;
    }
}
