package com.eduhub.edu_hub.admission.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.api.dto.request.ImportAdmissionApplicantsRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.exception.AdmissionImportScopeException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.application.mapper.AdmissionApplicantImportMapper;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.model.StudentAdmission;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionApplicantMergePolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionApplicantValidationPolicy;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionIdentityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.StudentAdmissionRepository;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetApplicant;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetReader;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.AdmissionSpreadsheetScope;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorResponse;
import com.eduhub.edu_hub.identity.api.dto.response.StudentAccountResponse;
import com.eduhub.edu_hub.identity.application.exception.IdentityAccountException;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionCohortInfo;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentInfo;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;

/** Kiểm tra luồng import bằng mock hạ tầng; không xác nhận khóa, rollback hoặc constraint database thực tế. */
class AdmissionImportServiceTest {
    private final AdmissionPlanLockService planLockService = mock(AdmissionPlanLockService.class);
    private final AdmissionApplicantRepository applicantRepository = mock(AdmissionApplicantRepository.class);
    private final StudentAdmissionRepository studentAdmissionRepository = mock(StudentAdmissionRepository.class);
    private final PeopleFacade.Admission peopleAdmissionFacade = mock(PeopleFacade.Admission.class);
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final CurriculumFacade.Majors majorCatalogFacade = mock(CurriculumFacade.Majors.class);
    private final AdmissionSpreadsheetReader spreadsheetReader = mock(AdmissionSpreadsheetReader.class);
    private final AdmissionPlanQueryService planQueryService = mock(AdmissionPlanQueryService.class);
    private final AdmissionAllocationService allocationService = mock(AdmissionAllocationService.class);
    private final AdmissionIdentityPolicy identityPolicy = new AdmissionIdentityPolicy();
    private final AdmissionApplicantImportMapper applicantMapper =
            Mappers.getMapper(AdmissionApplicantImportMapper.class);
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final Clock clock = spy(Clock.fixed(Instant.parse("2026-11-23T17:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
    private final AdmissionImportService importService = new AdmissionImportService(
            planLockService,
            applicantRepository,
            peopleAdmissionFacade,
            majorCatalogFacade,
            spreadsheetReader,
            new AdmissionApplicantClassificationService(
                    studentAdmissionRepository, peopleAdmissionFacade, identityFacade, identityPolicy),
            new AdmissionApplicantValidationPolicy(validatorFactory.getValidator()),
            new AdmissionApplicantMergePolicy(identityPolicy),
            applicantMapper,
            planQueryService,
            clock,
            allocationService);
    private final MockMultipartFile file =
            new MockMultipartFile("file", "applicants.xlsx", "application/octet-stream", new byte[] {1});
    private final AdmissionPlan plan =
            AdmissionPlan.builder().majorId(1L).cohortId(8L).revision(5).build();
    private List<AdmissionApplicant> savedApplicants = List.of();

    @BeforeEach
    void preparePlanAndCaptureSavedApplicants() {
        plan.setId(7L);
        when(planLockService.lockPlanAtRevision(7L, 5)).thenReturn(plan);
        when(majorCatalogFacade.requireMajor(1L)).thenReturn(new MajorResponse(1L, "CNTT", "Công nghệ thông tin"));
        when(peopleAdmissionFacade.requireCohort(8L)).thenReturn(new AdmissionCohortInfo(8L, "K08", 8));
        when(applicantRepository.findByPlan_IdAndDeletedFalseOrderByRowNumber(7L))
                .thenReturn(new ArrayList<>());
        when(applicantRepository.saveAll(anyList())).thenAnswer(invocation -> {
            savedApplicants = List.copyOf(invocation.getArgument(0));
            return savedApplicants;
        });
        when(planQueryService.buildPlanResponse(plan)).thenAnswer(invocation -> AdmissionPlanResponse.builder()
                .id(plan.getId())
                .revision(plan.getRevision())
                .state(plan.getState().name())
                .totalStudents(plan.getTotalStudents())
                .build());
    }

    @AfterEach
    void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void rejectsAnImportWhileEnrollmentIsRunningBeforeReadingTheFile() {
        plan.setState(AdmissionPlanStatus.CONFIRMING);

        assertThatThrownBy(this::importApplicants)
                .isInstanceOfSatisfying(AdmissionPlanException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ADMISSION_INVALID_STATE));

        verifyNoInteractions(spreadsheetReader, applicantRepository, studentAdmissionRepository, identityFacade);
        assertThat(plan.getRevision()).isEqualTo(5);
    }

    @Test
    void propagatesAStaleRevisionBeforeReadingOrReplacingApplicants() {
        var staleRevision = new AdmissionPlanException(ErrorCode.ADMISSION_STALE_PLAN);
        when(planLockService.lockPlanAtRevision(7L, 5)).thenThrow(staleRevision);

        assertThatThrownBy(this::importApplicants).isSameAs(staleRevision);

        verifyNoInteractions(spreadsheetReader, applicantRepository, majorCatalogFacade, peopleAdmissionFacade);
    }

    @ParameterizedTest
    @ValueSource(strings = {"majorCode", "cohortCode"})
    void checksThePlanScopeBeforeLoadingOrReplacingExistingApplicants(String mismatchedField) {
        var scopeError = new AdmissionImportScopeException(
                List.of(new FieldErrorDetail(mismatchedField, "Ngành hoặc khóa trong file không khớp")));
        when(spreadsheetReader.parse(eq(file), any(AdmissionSpreadsheetScope.class)))
                .thenThrow(scopeError);

        assertThatThrownBy(this::importApplicants).isSameAs(scopeError);

        verify(spreadsheetReader).parse(file, new AdmissionSpreadsheetScope("CNTT", "Công nghệ thông tin", "K08"));
        verifyNoInteractions(applicantRepository, studentAdmissionRepository, identityFacade, planQueryService);
        assertThat(plan.getRevision()).isEqualTo(5);
    }

    @Test
    void preservesInvalidRowsAndSkipsIdenticalDuplicatesForPreviouslyUnknownCitizenIds() {
        var firstApplicant = spreadsheetApplicant("HS01", "012345678901", 6);
        var duplicateApplicant = spreadsheetApplicant("HS01", "012345678901", 7);
        duplicateApplicant.setFullName("  NGUYỄN VĂN AN  ");
        duplicateApplicant.setEntranceScore(new BigDecimal("25.0"));
        var parseFailure = spreadsheetApplicant("HS02", "012345678902", 8);
        parseFailure.setState(AdmissionApplicantStatus.INVALID);
        parseFailure.setEntranceScore(null);
        parseFailure.setErrorMessage("Điểm không đọc được");
        var validationFailure = spreadsheetApplicant("HS03", "012345678903", 9);
        validationFailure.setEmail("invalid-email");
        parseApplicants(firstApplicant, duplicateApplicant, parseFailure, validationFailure);

        var response = importApplicants();

        assertThat(savedApplicants)
                .extracting(AdmissionApplicant::getState)
                .containsExactly(
                        AdmissionApplicantStatus.VALID,
                        AdmissionApplicantStatus.EXISTING,
                        AdmissionApplicantStatus.INVALID,
                        AdmissionApplicantStatus.INVALID);
        assertThat(savedApplicants).allMatch(applicant -> applicant.getPlan() == plan);
        assertThat(savedApplicants.get(2).getErrorMessage()).isEqualTo("Điểm không đọc được");
        assertThat(savedApplicants.get(3).getErrorMessage()).isEqualTo("Email sai định dạng");
        assertThat(response.revision()).isEqualTo(6);
        verify(studentAdmissionRepository).findByApplicationCodeIn(List.of("HS01"));
        verify(peopleAdmissionFacade).findStudentsByCitizenIds(List.of("012345678901"));
        var persistenceOrder = inOrder(applicantRepository, allocationService, planQueryService);
        persistenceOrder.verify(applicantRepository).saveAll(anyList());
        persistenceOrder.verify(applicantRepository).flush();
        persistenceOrder.verify(allocationService).prepareAndAllocateApplicants(eq(plan), anyList());
        persistenceOrder.verify(planQueryService).buildPlanResponse(plan);
        verify(applicantRepository, never()).deleteByPlan_Id(anyLong());
    }

    @Test
    void propagatesAChangedApplicationCodeConflictToEveryDuplicateInTheFile() {
        var changedApplicant = spreadsheetApplicant("HS01", "012345678902", 8);
        parseApplicants(
                spreadsheetApplicant("HS01", "012345678901", 6),
                spreadsheetApplicant("HS01", "012345678901", 7),
                changedApplicant,
                spreadsheetApplicant("HS02", "012345678903", 9));

        importApplicants();

        assertThat(savedApplicants.subList(0, 3))
                .allMatch(applicant -> applicant.getState() == AdmissionApplicantStatus.CONFLICT);
        assertThat(savedApplicants.get(3).getState()).isEqualTo(AdmissionApplicantStatus.VALID);
    }

    @Test
    void checksExistingApplicationCodesEvenWhenTheIncomingCitizenIdHasNoMatchingStudent() {
        var identicalApplicant = spreadsheetApplicant("HS01", "012345678901", 6);
        var changedApplicant = spreadsheetApplicant("HS02", "012345678902", 7);
        when(studentAdmissionRepository.findByApplicationCodeIn(List.of("HS01", "HS02")))
                .thenReturn(List.of(
                        admissionFor(identicalApplicant),
                        admissionFor(spreadsheetApplicant("HS02", "012345678903", 7))));
        parseApplicants(identicalApplicant, changedApplicant);

        importApplicants();

        assertThat(savedApplicants)
                .extracting(AdmissionApplicant::getState)
                .containsExactly(AdmissionApplicantStatus.EXISTING, AdmissionApplicantStatus.CONFLICT);
        verifyNoInteractions(identityFacade);
    }

    @Test
    void propagatesCitizenIdConflictsThroughLinkedApplicationCodes() {
        parseApplicants(
                spreadsheetApplicant("HS01", "012345678901", 6),
                spreadsheetApplicant("HS02", "012345678901", 7),
                spreadsheetApplicant("HS02", "012345678902", 8),
                spreadsheetApplicant("HS03", "012345678903", 9));

        importApplicants();

        assertThat(savedApplicants.subList(0, 3))
                .allMatch(applicant -> applicant.getState() == AdmissionApplicantStatus.CONFLICT);
        assertThat(savedApplicants.get(3).getState()).isEqualTo(AdmissionApplicantStatus.VALID);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reusesAnExistingStudentAndKeepsNameAndAccountWarnings(boolean locked) {
        var existingStudent = student(31L, 41L, "012345678901", false);
        when(peopleAdmissionFacade.findStudentsByCitizenIds(List.of("012345678901")))
                .thenReturn(List.of(existingStudent, existingStudent));
        when(identityFacade.getStudentAccount(41L))
                .thenReturn(new StudentAccountResponse(
                        41L, "CNTT080101", null, locked ? "LOCKED" : "ACTIVE", false, locked));
        parseApplicants(spreadsheetApplicant("HS01", "012345678901", 6));

        importApplicants();

        var applicant = savedApplicants.getFirst();
        assertThat(applicant.getState()).isEqualTo(AdmissionApplicantStatus.VALID);
        assertThat(applicant.getStudentId()).isEqualTo(31L);
        assertThat(applicant.isReuseAccount()).isTrue();
        assertThat(applicant.getWarningMessage()).startsWith("Họ tên khác hồ sơ danh tính cũ; giữ nguyên hồ sơ gốc");
        assertThat(applicant.getWarningMessage().contains("Tài khoản cũ đang khóa/ngừng hoạt động"))
                .isEqualTo(locked);
        verify(identityFacade).getStudentAccount(41L);
        verifyNoMoreInteractions(identityFacade);
    }

    @Test
    void preservesReusedAccountMetadataWhenALaterDuplicateMakesTheFirstApplicantConflicting() {
        when(peopleAdmissionFacade.findStudentsByCitizenIds(anyList()))
                .thenReturn(List.of(student(31L, 41L, "012345678901", false)));
        when(identityFacade.getStudentAccount(41L))
                .thenReturn(new StudentAccountResponse(41L, "CNTT080101", null, "LOCKED", false, true));
        var changedApplicant = spreadsheetApplicant("HS01", "012345678901", 7);
        changedApplicant.setEntranceScore(new BigDecimal("26.00"));
        parseApplicants(spreadsheetApplicant("HS01", "012345678901", 6), changedApplicant);

        importApplicants();

        assertThat(savedApplicants).allMatch(applicant -> applicant.getState() == AdmissionApplicantStatus.CONFLICT);
        assertThat(savedApplicants.getFirst().getStudentId()).isEqualTo(31L);
        assertThat(savedApplicants.getFirst().isReuseAccount()).isTrue();
        assertThat(savedApplicants.getFirst().getWarningMessage()).contains("Tài khoản cũ đang khóa/ngừng hoạt động");
        assertThat(savedApplicants.getLast().isReuseAccount()).isFalse();
        verify(identityFacade).getStudentAccount(41L);
    }

    @Test
    void rejectsAmbiguousDeletedAndAccountlessStudentIdentities() {
        when(peopleAdmissionFacade.findStudentsByCitizenIds(anyList()))
                .thenReturn(List.of(
                        student(31L, 41L, "012345678901", false),
                        student(32L, 42L, "012345678901", false),
                        student(33L, 43L, "012345678902", true),
                        student(34L, null, "012345678903", false)));
        parseApplicants(
                spreadsheetApplicant("HS01", "012345678901", 6),
                spreadsheetApplicant("HS02", "012345678902", 7),
                spreadsheetApplicant("HS03", "012345678903", 8));

        importApplicants();

        assertThat(savedApplicants).allMatch(applicant -> applicant.getState() == AdmissionApplicantStatus.CONFLICT);
        verifyNoInteractions(identityFacade);
    }

    @Test
    void keepsOtherApplicantsWhenAnExistingStudentAccountIsUnavailable() {
        when(peopleAdmissionFacade.findStudentsByCitizenIds(anyList()))
                .thenReturn(List.of(student(31L, 41L, "012345678901", false)));
        when(identityFacade.getStudentAccount(41L)).thenThrow(new IdentityAccountException(ErrorCode.USER_NOT_FOUND));
        parseApplicants(
                spreadsheetApplicant("HS01", "012345678901", 6), spreadsheetApplicant("HS02", "012345678902", 7));

        importApplicants();

        assertThat(savedApplicants)
                .extracting(AdmissionApplicant::getState)
                .containsExactly(AdmissionApplicantStatus.CONFLICT, AdmissionApplicantStatus.VALID);
    }

    @Test
    void propagatesInfrastructureFailuresBeforeDeletingOrSavingApplicants() {
        when(peopleAdmissionFacade.findStudentsByCitizenIds(anyList()))
                .thenReturn(List.of(student(31L, 41L, "012345678901", false)));
        var infrastructureFailure = new DataAccessResourceFailureException("Không truy cập được nguồn tài khoản");
        when(identityFacade.getStudentAccount(41L)).thenThrow(infrastructureFailure);
        parseApplicants(spreadsheetApplicant("HS01", "012345678901", 6));

        assertThatThrownBy(this::importApplicants).isSameAs(infrastructureFailure);

        verify(applicantRepository, never()).deleteByPlan_Id(anyLong());
        verify(applicantRepository, never()).saveAll(anyList());
        assertThat(plan.getRevision()).isEqualTo(5);
    }

    @Test
    void skipsIdentityLookupsWhenEveryImportedApplicantIsInvalid() {
        var invalidApplicant = spreadsheetApplicant("HS01", "012345678901", 6);
        invalidApplicant.setEntranceScore(null);
        parseApplicants(invalidApplicant);

        importApplicants();

        assertThat(savedApplicants).singleElement().satisfies(applicant -> assertThat(applicant.getState())
                .isEqualTo(AdmissionApplicantStatus.INVALID));
        verifyNoInteractions(studentAdmissionRepository, identityFacade);
        verify(peopleAdmissionFacade, never()).findStudentsByCitizenIds(anyList());
    }

    @Test
    void limitsEachAdmissionAndStudentLookupToFiveHundredApplicants() {
        List<AdmissionSpreadsheetApplicant> applicants = IntStream.rangeClosed(1, 501)
                .mapToObj(index -> spreadsheetApplicant("HS" + index, "%012d".formatted(index), index + 5))
                .toList();
        when(spreadsheetReader.parse(eq(file), any(AdmissionSpreadsheetScope.class)))
                .thenReturn(applicants);

        importApplicants();

        verify(studentAdmissionRepository)
                .findByApplicationCodeIn(applicants.subList(0, 500).stream()
                        .map(AdmissionSpreadsheetApplicant::getApplicationCode)
                        .toList());
        verify(studentAdmissionRepository).findByApplicationCodeIn(List.of("HS501"));
        verifyNoMoreInteractions(studentAdmissionRepository);
        verify(peopleAdmissionFacade)
                .findStudentsByCitizenIds(applicants.subList(0, 500).stream()
                        .map(AdmissionSpreadsheetApplicant::getCitizenId)
                        .toList());
        verify(peopleAdmissionFacade).findStudentsByCitizenIds(List.of("000000000501"));
    }

    @Test
    void preservesEnrolledAndFailedApplicantsInsteadOfOverwritingThemFromASupplementalFile() {
        var enrolledSource = spreadsheetApplicant("HS01", "012345678901", 6);
        var enrolledApplicant = applicantMapper.fromSpreadsheetApplicant(enrolledSource);
        enrolledApplicant.setState(AdmissionApplicantStatus.ENROLLED);
        enrolledApplicant.setStudentId(31L);
        enrolledApplicant.setAdministrativeClassId(51L);
        var failedApplicant = applicantMapper.fromSpreadsheetApplicant(spreadsheetApplicant("HS02", "012345678902", 7));
        failedApplicant.setState(AdmissionApplicantStatus.FAILED);
        failedApplicant.setId(12L);
        var correctedSource = spreadsheetApplicant("HS02", "012345678902", 10);
        correctedSource.setFullName("Trần Văn Bình");
        when(applicantRepository.findByPlan_IdAndDeletedFalseOrderByRowNumber(7L))
                .thenReturn(new ArrayList<>(List.of(enrolledApplicant, failedApplicant)));
        when(applicantRepository.findMaximumRowNumberByPlanIdIncludingDeleted(7L))
                .thenReturn(7);
        when(studentAdmissionRepository.findByApplicationCodeIn(anyList()))
                .thenReturn(List.of(admissionFor(enrolledSource)));
        plan.setState(AdmissionPlanStatus.PARTIALLY_CONFIRMED);
        plan.setSelectedClassCount(1);
        parseApplicants(enrolledSource, correctedSource);

        importApplicants();

        assertThat(savedApplicants).hasSize(3);
        assertThat(savedApplicants.subList(0, 2)).containsExactly(enrolledApplicant, failedApplicant);
        assertThat(savedApplicants.getLast().getState()).isEqualTo(AdmissionApplicantStatus.CONFLICT);
        assertThat(enrolledApplicant.getState()).isEqualTo(AdmissionApplicantStatus.ENROLLED);
        assertThat(enrolledApplicant.getAdministrativeClassId()).isEqualTo(51L);
        assertThat(enrolledApplicant.getStudentId()).isEqualTo(31L);
        assertThat(failedApplicant.getId()).isEqualTo(12L);
        assertThat(failedApplicant.getRowNumber()).isEqualTo(7);
        assertThat(failedApplicant.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(failedApplicant.getState()).isEqualTo(AdmissionApplicantStatus.FAILED);
        verify(allocationService).prepareAndAllocateApplicants(eq(plan), anyList());
        verify(applicantRepository, never()).deleteByPlan_Id(anyLong());
    }

    @Test
    void keepsThePlanConfirmedWhenAnEquivalentReimportAddsNoPendingApplicants() {
        var source = spreadsheetApplicant("HS01", "012345678901", 6);
        var enrolledApplicant = applicantMapper.fromSpreadsheetApplicant(source);
        enrolledApplicant.setState(AdmissionApplicantStatus.ENROLLED);
        when(applicantRepository.findByPlan_IdAndDeletedFalseOrderByRowNumber(7L))
                .thenReturn(new ArrayList<>(List.of(enrolledApplicant)));
        when(applicantRepository.findMaximumRowNumberByPlanIdIncludingDeleted(7L))
                .thenReturn(6);
        when(studentAdmissionRepository.findByApplicationCodeIn(anyList())).thenReturn(List.of(admissionFor(source)));
        plan.setState(AdmissionPlanStatus.CONFIRMED);
        parseApplicants(source);

        importApplicants();

        assertThat(savedApplicants).containsExactly(enrolledApplicant);
        assertThat(plan.getState()).isEqualTo(AdmissionPlanStatus.CONFIRMED);
        assertThat(plan.getRevision()).isEqualTo(6);
        verify(applicantRepository, never()).deleteByPlan_Id(anyLong());
    }

    @Test
    void reallocatesAfterAppendingApplicants() {
        plan.setState(AdmissionPlanStatus.PREVIEWED);
        plan.setSelectedClassCount(2);
        parseApplicants(spreadsheetApplicant("HS01", "012345678901", 6));

        importApplicants();

        verify(allocationService).prepareAndAllocateApplicants(eq(plan), anyList());
        verify(applicantRepository, never()).deleteByPlan_Id(7L);
    }

    @Test
    void usesOneVietnameseValidationDateForEveryApplicant() {
        var firstApplicant = spreadsheetApplicant("HS01", "012345678901", 6);
        firstApplicant.setDateOfBirth(LocalDate.of(2026, 11, 23));
        var secondApplicant = spreadsheetApplicant("HS02", "012345678902", 7);
        secondApplicant.setDateOfBirth(LocalDate.of(2026, 11, 23));
        parseApplicants(firstApplicant, secondApplicant);

        importApplicants();

        assertThat(savedApplicants).allMatch(applicant -> applicant.getState() == AdmissionApplicantStatus.VALID);
        verify(clock).instant();
    }

    @Test
    void reimportsARemovedIdentityAsANewApplicantAfterTheHistoricalMaximumRowNumber() {
        when(applicantRepository.findMaximumRowNumberByPlanIdIncludingDeleted(7L))
                .thenReturn(20);
        parseApplicants(spreadsheetApplicant("HS01", "012345678901", 6));

        importApplicants();

        assertThat(savedApplicants).singleElement().satisfies(applicant -> {
            assertThat(applicant.getApplicationCode()).isEqualTo("HS01");
            assertThat(applicant.getState()).isEqualTo(AdmissionApplicantStatus.VALID);
            assertThat(applicant.getRowNumber()).isEqualTo(21);
            assertThat(applicant.isDeleted()).isFalse();
        });
        verify(applicantRepository).findByPlan_IdAndDeletedFalseOrderByRowNumber(7L);
        verify(applicantRepository).findMaximumRowNumberByPlanIdIncludingDeleted(7L);
        verify(allocationService).prepareAndAllocateApplicants(eq(plan), eq(savedApplicants));
    }

    private AdmissionPlanResponse importApplicants() {
        return importService.importApplicants(7L, new ImportAdmissionApplicantsRequest(5, file));
    }

    private void parseApplicants(AdmissionSpreadsheetApplicant... applicants) {
        when(spreadsheetReader.parse(eq(file), any(AdmissionSpreadsheetScope.class)))
                .thenReturn(List.of(applicants));
    }

    private AdmissionSpreadsheetApplicant spreadsheetApplicant(
            String applicationCode, String citizenId, int rowNumber) {
        return AdmissionSpreadsheetApplicant.builder()
                .applicationCode(applicationCode)
                .citizenId(citizenId)
                .fullName("Nguyễn Văn An")
                .entranceScore(new BigDecimal("25.00"))
                .rowNumber(rowNumber)
                .build();
    }

    private StudentAdmission admissionFor(AdmissionSpreadsheetApplicant applicant) {
        return StudentAdmission.builder()
                .applicationCode(applicant.getApplicationCode())
                .citizenId(applicant.getCitizenId())
                .fullName(applicant.getFullName())
                .entranceScore(applicant.getEntranceScore())
                .studentId(31L)
                .build();
    }

    private AdmissionStudentInfo student(Long studentId, Long userId, String citizenId, boolean deleted) {
        return AdmissionStudentInfo.builder()
                .id(studentId)
                .userId(userId)
                .citizenId(citizenId)
                .fullName("Tên hồ sơ gốc")
                .deleted(deleted)
                .build();
    }
}
