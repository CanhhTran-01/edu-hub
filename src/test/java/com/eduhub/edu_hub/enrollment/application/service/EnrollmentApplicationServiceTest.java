package com.eduhub.edu_hub.enrollment.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.*;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.*;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.enrollment.api.dto.request.RegisterModuleClassRequest;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.enrollment.infrastructure.config.EnrollmentRegistrationSettings;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.response.EnrollmentStudentResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.response.*;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class EnrollmentApplicationServiceTest {
    @Mock
    EnrollmentRepository enrollmentRepository;

    @Mock
    IdentityFacade identityFacade;

    @Mock
    PeopleFacade.Enrollment peopleFacade;

    @Mock
    CurriculumFacade.Catalog curriculumCatalog;

    @Mock
    CurriculumFacade.Registration curriculumRegistration;

    @Mock
    SchedulingFacade.Registration schedulingFacade;

    private final EnrollmentRegistrationSettings settings = new EnrollmentRegistrationSettings();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-31T17:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final Jwt principal =
            Jwt.withTokenValue("token").header("alg", "HS256").subject("sv").build();
    private EnrollmentApplicationService service;
    private final com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService systemConfigService =
            com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData.configuredService();
    private final EnrollmentStudentResponse student = new EnrollmentStudentResponse(
            10L, "SV10", "Sinh viên", 1L, 2L, TrainingType.CHINH_QUY, 2026, ActivityStatus.STUDYING);

    @BeforeEach
    void setUp() {
        // Các con số dưới đây chỉ là quy chế của tình huống unit test, không phải mặc định runtime.
        settings.setFirstSupportedAdmissionYear(2026);
        service = new EnrollmentApplicationService(
                enrollmentRepository,
                identityFacade,
                peopleFacade,
                curriculumCatalog,
                curriculumRegistration,
                schedulingFacade,
                settings,
                systemConfigService,
                clock);
    }

    @Test
    void rejectsMissingPrincipalBeforeAnyDataAccess() {
        assertError(() -> service.register(null, request()), ErrorCode.UNAUTHENTICATED);
        verifyNoInteractions(enrollmentRepository, peopleFacade, schedulingFacade);
    }

    @Test
    void registersWithCurrentCreditsAndWarnsBelowMinimumOnVietnamClosingDay() {
        prepareRegistration(List.of());
        when(enrollmentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Enrollment enrollment = invocation.getArgument(0);
            enrollment.setId(90L);
            return enrollment;
        });
        var response = service.register(principal, request());
        assertThat(response.registeredCredits()).isEqualTo(3);
        assertThat(response.warnings()).hasSize(1);
        assertThat(response.enrollment().getStatus()).isEqualTo("REGISTERED");
        assertThat(response.enrollment().getCredits()).isEqualTo(3);
        assertThat(response.enrollment().getRegisteredAt()).isEqualTo(LocalDateTime.parse("2026-09-01T00:30:00"));
        var savedEnrollment = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).saveAndFlush(savedEnrollment.capture());
        assertThat(savedEnrollment.getValue().getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-08-31T17:30:00"));
        assertThat(savedEnrollment.getValue().getUpdatedAt()).isEqualTo(LocalDateTime.parse("2026-08-31T17:30:00"));
        var order = inOrder(schedulingFacade, peopleFacade, enrollmentRepository);
        order.verify(schedulingFacade).findSemesterIdByModuleClassId(100L);
        order.verify(schedulingFacade).lockSemester(7L);
        order.verify(peopleFacade).lockStudentByUserId(1L);
        order.verify(schedulingFacade).lockModuleClass(100L);
        order.verify(enrollmentRepository).findAllByStudentIdOrderByRegisteredAtAscIdAsc(10L);
        order.verify(schedulingFacade).findScheduleConflicts(100L, Set.of());
        order.verify(schedulingFacade).reserveSeat(100L);
        order.verify(enrollmentRepository).saveAndFlush(any());
    }

    @Test
    void repeatedRegistrationDoesNotReserveOrInsertTwice() {
        authenticate();
        when(schedulingFacade.findSemesterIdByModuleClassId(100L)).thenReturn(7L);
        when(schedulingFacade.lockSemester(7L)).thenReturn(semester());
        when(peopleFacade.lockStudentByUserId(1L)).thenReturn(student);
        when(schedulingFacade.lockModuleClass(100L)).thenReturn(moduleClass());
        var existing = enrollment(EnrollmentStatus.REGISTERED, 100L, 5L, 3);
        when(enrollmentRepository.findAllByStudentIdOrderByRegisteredAtAscIdAsc(10L))
                .thenReturn(List.of(existing));
        assertThat(service.register(principal, request()).registeredCredits()).isEqualTo(3);
        verify(schedulingFacade, never()).reserveSeat(anyLong());
        verify(enrollmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnotherClassOfTheSameSubject() {
        prepareRegistrationBeforeSubjectRules(List.of(enrollment(EnrollmentStatus.REGISTERED, 101L, 5L, 3)));
        assertError(() -> service.register(principal, request()), ErrorCode.ALREADY_ENROLLED);
        verify(schedulingFacade, never()).reserveSeat(anyLong());
    }

    @Test
    void rejectsPreviouslyPassedSubjectAndKeepsHistory() {
        prepareRegistrationBeforeSubjectRules(List.of(enrollment(EnrollmentStatus.COMPLETED, 90L, 5L, 2)));
        assertError(() -> service.register(principal, request()), ErrorCode.ENROLLMENT_SUBJECT_COMPLETED);
        verify(enrollmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void retakeUsesThreeCreditsRatherThanTwoFromFailedAttempt() {
        prepareRegistration(List.of(enrollment(EnrollmentStatus.FAILED, 90L, 5L, 2)));
        when(enrollmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.register(principal, request()).enrollment().getCredits())
                .isEqualTo(3);
    }

    @Test
    void reportsMissingPrerequisitesWithoutWriting() {
        prepareRegistrationBeforeSubjectRules(List.of());
        var subject = subject();
        subject.setPrerequisites(List.of(SubjectSimpleResponse.builder()
                .id(4L)
                .subjectCode("CS4")
                .subjectName("Môn trước")
                .build()));
        when(curriculumRegistration.requireRegistrationSubjects(Set.of(5L))).thenReturn(List.of(subject));
        assertThatThrownBy(() -> service.register(principal, request()))
                .isInstanceOfSatisfying(EnrollmentException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PREREQUISITE_NOT_MET);
                    assertThat(exception.getErrors()).singleElement().satisfies(error -> assertThat(error.getMessage())
                            .contains("CS4"));
                });
        verify(schedulingFacade, never()).reserveSeat(anyLong());
    }

    @Test
    void rejectsCreditOverflowBeforeReservingSeat() {
        prepareRegistration(List.of(enrollment(EnrollmentStatus.REGISTERED, 101L, 6L, 18)));
        assertError(() -> service.register(principal, request()), ErrorCode.ENROLLMENT_CREDIT_LIMIT_EXCEEDED);
        verify(schedulingFacade, never()).reserveSeat(anyLong());
    }

    @Test
    void readsChangedCreditLimitsOnNextRegistrationAndReturnsTheSameLimitsUsedForValidation() {
        prepareRegistration(List.of());
        when(systemConfigService.getSnapshot())
                .thenReturn(com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData.snapshot(Map.of(
                        com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.ENROLLMENT_MINIMUM_CREDITS,
                                "0",
                        com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.ENROLLMENT_MAXIMUM_CREDITS,
                                "2")));
        assertError(() -> service.register(principal, request()), ErrorCode.ENROLLMENT_CREDIT_LIMIT_EXCEEDED);
        verify(schedulingFacade, never()).reserveSeat(anyLong());
        when(systemConfigService.getSnapshot())
                .thenReturn(com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData.snapshot(Map.of(
                        com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.ENROLLMENT_MINIMUM_CREDITS,
                                "0",
                        com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.ENROLLMENT_MAXIMUM_CREDITS,
                                "4")));
        when(enrollmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.register(principal, request());
        assertThat(response.maximumCredits()).isEqualTo(4);
        assertThat(response.minimumCredits()).isZero();
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void rejectsFullClass() {
        prepareRegistration(List.of());
        var moduleClass = moduleClass();
        moduleClass.setCurrentSize(40);
        when(schedulingFacade.lockModuleClass(100L)).thenReturn(moduleClass);
        assertError(() -> service.register(principal, request()), ErrorCode.CAPACITY_FULL);
        verify(schedulingFacade, never()).reserveSeat(anyLong());
    }

    @Test
    void rejectsRegistrationAfterClosingDayBeforeStudentLock() {
        authenticate();
        when(schedulingFacade.findSemesterIdByModuleClassId(100L)).thenReturn(7L);
        var semester = semester();
        semester.setRegistrationEnd(LocalDate.of(2026, 8, 31));
        when(schedulingFacade.lockSemester(7L)).thenReturn(semester);
        assertError(() -> service.register(principal, request()), ErrorCode.REGISTRATION_CLOSED);
        verifyNoInteractions(peopleFacade, enrollmentRepository);
    }

    @Test
    void dropsOwnedEnrollmentOnceAndPreservesRecord() {
        prepareDrop();
        var enrollment = enrollment(EnrollmentStatus.REGISTERED, 100L, 5L, 3);
        when(enrollmentRepository.findByIdAndStudentIdAndDeletedFalse(90L, 10L)).thenReturn(Optional.of(enrollment));
        when(schedulingFacade.lockModuleClass(100L)).thenReturn(moduleClass());
        when(enrollmentRepository.findAllByStudentIdOrderByRegisteredAtAscIdAsc(10L))
                .thenReturn(List.of(enrollment));
        var response = service.drop(principal, 90L);
        assertThat(response.enrollment().getStatus()).isEqualTo("DROPPED");
        assertThat(response.enrollment().getDroppedAt()).isEqualTo(LocalDateTime.parse("2026-09-01T00:30:00"));
        assertThat(enrollment.getUpdatedAt()).isEqualTo(LocalDateTime.parse("2026-08-31T17:30:00"));
        assertThat(response.registeredCredits()).isZero();
        verify(schedulingFacade).releaseSeat(100L);
        service.drop(principal, 90L);
        verify(schedulingFacade, times(1)).releaseSeat(100L);
        verify(enrollmentRepository, never()).delete(any(Enrollment.class));
    }

    @Test
    void cannotDropEnrollmentBelongingToAnotherStudent() {
        authenticate();
        when(peopleFacade.findStudentByUserId(1L)).thenReturn(student);
        assertError(() -> service.drop(principal, 90L), ErrorCode.ENROLLMENT_NOT_FOUND);
        verifyNoInteractions(schedulingFacade);
        verify(peopleFacade, never()).lockStudentByUserId(anyLong());
    }

    private void prepareDrop() {
        authenticate();
        when(peopleFacade.findStudentByUserId(1L)).thenReturn(student);
        when(enrollmentRepository.findSemesterIdByIdAndStudentId(90L, 10L)).thenReturn(Optional.of(7L));
        when(schedulingFacade.lockSemester(7L)).thenReturn(semester());
        when(peopleFacade.lockStudentByUserId(1L)).thenReturn(student);
    }

    private void prepareRegistration(List<Enrollment> history) {
        prepareRegistrationBeforeSubjectRules(history);
        when(curriculumRegistration.requireRegistrationSubjects(Set.of(5L))).thenReturn(List.of(subject()));
    }

    private void prepareRegistrationBeforeSubjectRules(List<Enrollment> history) {
        authenticate();
        when(schedulingFacade.findSemesterIdByModuleClassId(100L)).thenReturn(7L);
        when(schedulingFacade.lockSemester(7L)).thenReturn(semester());
        when(peopleFacade.lockStudentByUserId(1L)).thenReturn(student);
        when(schedulingFacade.lockModuleClass(100L)).thenReturn(moduleClass());
        when(enrollmentRepository.findAllByStudentIdOrderByRegisteredAtAscIdAsc(10L))
                .thenReturn(history);
        when(curriculumCatalog.findCurriculum(1L, 2L, TrainingType.CHINH_QUY))
                .thenReturn(Optional.of(CurriculumResponse.builder()
                        .subjects(List.of(CurriculumSubjectResponse.builder()
                                .subjectId(5L)
                                .build()))
                        .build()));
    }

    private void authenticate() {
        when(identityFacade.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(1L).build());
    }

    private RegisterModuleClassRequest request() {
        var request = new RegisterModuleClassRequest();
        request.setModuleClassId(100L);
        return request;
    }

    private SemesterResponse semester() {
        return SemesterResponse.builder()
                .id(7L)
                .semesterCode("HK_1_2026_2027")
                .registrationStart(LocalDate.of(2026, 8, 20))
                .registrationEnd(LocalDate.of(2026, 9, 1))
                .isLocked(false)
                .build();
    }

    private ModuleClassResponse moduleClass() {
        return ModuleClassResponse.builder()
                .id(100L)
                .semesterId(7L)
                .subjectId(5L)
                .moduleClassCode("MC100")
                .lecturerId(8L)
                .maxCapacity(40)
                .currentSize(0)
                .status(ModuleClassStatus.OPEN)
                .schedules(List.of(ClassScheduleResponse.builder().id(1L).build()))
                .build();
    }

    private SubjectResponse subject() {
        return SubjectResponse.builder()
                .id(5L)
                .subjectCode("CS5")
                .subjectName("Môn mới")
                .credits(3)
                .prerequisites(List.of())
                .build();
    }

    private Enrollment enrollment(EnrollmentStatus status, Long classId, Long subjectId, int credits) {
        var enrollment = Enrollment.builder()
                .studentId(10L)
                .moduleClassId(classId)
                .subjectId(subjectId)
                .semesterId(7L)
                .credits(credits)
                .status(status)
                .build();
        enrollment.setId(90L);
        return enrollment;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode error) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(EnrollmentException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(error));
    }
}
