package com.eduhub.edu_hub.enrollment.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.enrollment.api.dto.request.*;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import com.eduhub.edu_hub.enrollment.domain.policy.CourseGradePolicy.GradeBand;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.enrollment.infrastructure.config.CourseGradeSettings;
import com.eduhub.edu_hub.enrollment.infrastructure.spreadsheet.CourseGradeSpreadsheet;
import com.eduhub.edu_hub.enrollment.infrastructure.spreadsheet.CourseGradeSpreadsheet.*;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.response.StudentRosterProfileResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassGradingResponse;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CourseGradeServiceTest {
    private final EnrollmentRepository repository = mock(EnrollmentRepository.class);
    private final IdentityFacade identity = mock(IdentityFacade.class);
    private final PeopleFacade.Enrollment students = mock(PeopleFacade.Enrollment.class);
    private final SchedulingFacade.Grading scheduling = mock(SchedulingFacade.Grading.class);
    private final CourseGradeSpreadsheet spreadsheet = mock(CourseGradeSpreadsheet.class);
    private final CourseGradeSettings settings = new CourseGradeSettings();
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final LocalDateTime currentBusinessTime = LocalDateTime.now(clock);
    private final CourseGradeService service = new CourseGradeService(
            repository, identity, students, scheduling, spreadsheet, settings, transactions, clock);
    private final Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("academic-admin")
            .claim("token_use", "ACCESS")
            .build();
    private final ModuleClassGradingResponse moduleClass =
            new ModuleClassGradingResponse(100L, "MC_100", 7L, "HK_1_2026_2027", 70L, ModuleClassStatus.COMPLETED);
    private final Enrollment firstEnrollment = enrollment(10L, 1L);
    private final Enrollment secondEnrollment = enrollment(11L, 2L);

    @BeforeEach
    void setup() {
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(identity.requireCurrentUser(principal))
                .thenReturn(
                        UserInfoResponse.builder().id(9L).role("ACADEMIC_ADMIN").build());
        when(scheduling.findModuleClassForGrading(100L)).thenReturn(moduleClass);
        when(scheduling.lockModuleClassForGrading(100L)).thenReturn(moduleClass);
        when(repository.findModuleClassIdByEnrollmentId(10L)).thenReturn(Optional.of(100L));
        when(repository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(firstEnrollment));
        when(repository.findAllByModuleClassIdAndDeletedFalseAndStatusInOrderByIdAsc(eq(100L), anyCollection()))
                .thenReturn(List.of(firstEnrollment, secondEnrollment));
        when(students.findRosterStudentsByIds(anySet())).thenAnswer(invocation -> {
            Set<Long> ids = invocation.getArgument(0);
            return ids.stream()
                    .sorted()
                    .map(studentId -> new StudentRosterProfileResponse(
                            studentId, studentId == 1 ? "00001" : "00002", "Sinh viên " + studentId))
                    .toList();
        });
        // Thang thử nghiệm được truyền tường minh; ứng dụng không có thang mặc định ngầm.
        settings.setGradeScale(
                List.of(band("0", "F", "0"), band("4", "D", "1"), band("7", "B", "3"), band("9", "A", "4")));
    }

    @Test
    void updatesOnlyTheSelectedAttemptAndPublishesForFourteenDays() {
        var response = service.updateCourseGrade(principal, 10L, request("9"));
        assertThat(response.enrollmentId()).isEqualTo(10L);
        assertThat(response.registeredAt()).isEqualTo(firstEnrollment.getRegisteredAt());
        assertThat(response.finalExamScore()).isEqualByComparingTo("9");
        assertThat(response.courseScore10()).isEqualByComparingTo("8.4");
        assertThat(response.status()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(response.gradePublishedAt()).isEqualTo(currentBusinessTime);
        assertThat(response.gradeLockAt()).isEqualTo(currentBusinessTime.plusDays(14));
        assertThat(response.gradeLocked()).isFalse();
        assertThat(firstEnrollment.getUpdatedAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        assertThat(secondEnrollment.getFinalExamScore()).isNull();
        var order = inOrder(repository, scheduling);
        order.verify(repository).findModuleClassIdByEnrollmentId(10L);
        order.verify(scheduling).lockModuleClassForGrading(100L);
        order.verify(repository).findByIdAndDeletedFalse(10L);
        order.verify(repository).flush();
    }

    @Test
    void correctionMayChangePassToFailWithoutExtendingTheOriginalDeadline() {
        var publishedAt = currentBusinessTime.minusDays(10);
        firstEnrollment.updateCourseGrade(
                new BigDecimal("9"),
                new BigDecimal("8.4"),
                "B",
                new BigDecimal("3"),
                EnrollmentStatus.COMPLETED,
                publishedAt,
                publishedAt.plusDays(14),
                publishedAt);
        settings.setGradeCorrectionWindow(Duration.ofDays(90));
        var response = service.updateCourseGrade(principal, 10L, request("0"));
        assertThat(response.status()).isEqualTo(EnrollmentStatus.FAILED);
        assertThat(response.courseScore4()).isZero();
        assertThat(response.gradePublishedAt()).isEqualTo(publishedAt);
        assertThat(response.gradeLockAt()).isEqualTo(publishedAt.plusDays(14));
    }

    @Test
    void refusesUpdatesAtTheExactDeadlineWithoutDependingOnAScheduler() {
        firstEnrollment.updateCourseGrade(
                new BigDecimal("9"),
                new BigDecimal("8.4"),
                "B",
                new BigDecimal("3"),
                EnrollmentStatus.COMPLETED,
                currentBusinessTime.minusDays(14),
                currentBusinessTime,
                currentBusinessTime.minusDays(14));
        assertError(() -> service.updateCourseGrade(principal, 10L, request("7")), ErrorCode.GRADE_LOCKED);
        assertThat(firstEnrollment.getFinalExamScore()).isEqualByComparingTo("9");
        verify(repository, never()).flush();
    }

    @Test
    void doesNotInventAPublicationTimeForAnExistingLegacyGrade() {
        var legacy = Enrollment.builder()
                .studentId(1L)
                .moduleClassId(100L)
                .status(EnrollmentStatus.COMPLETED)
                .processScore(new BigDecimal("7"))
                .courseScore10(new BigDecimal("8"))
                .build();
        legacy.setId(10L);
        when(repository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(legacy));
        assertError(() -> service.updateCourseGrade(principal, 10L, request("7")), ErrorCode.GRADE_LOCKED);
        assertThat(legacy.getGradePublishedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "LECTURER", "SYSTEM_ADMIN"})
    void refusesOtherRolesForReadingWritingAndImporting(String role) {
        when(identity.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(9L).role(role).build());
        assertError(() -> service.findCourseGrades(principal, new SearchCourseGradesRequest()), ErrorCode.UNAUTHORIZED);
        assertError(() -> service.updateCourseGrade(principal, 10L, request("7")), ErrorCode.UNAUTHORIZED);
        assertError(
                () -> service.importCourseGrades(principal, 100L, new ImportCourseGradesRequest(null)),
                ErrorCode.UNAUTHORIZED);
        assertError(() -> service.createTemplate(principal, 100L), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(repository, spreadsheet, scheduling);
    }

    @Test
    void refusesMissingAndPasswordChangePrincipals() {
        assertError(() -> service.findCourseGrades(null, new SearchCourseGradesRequest()), ErrorCode.UNAUTHENTICATED);
        var restricted = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("token_use", "PASSWORD_CHANGE")
                .build();
        assertError(() -> service.updateCourseGrade(restricted, 10L, request("7")), ErrorCode.FIRST_LOGIN_REQUIRED);
        verifyNoInteractions(identity, repository);
    }

    @Test
    void usesPeopleLookupBeforePagingAndReturnsTheExactAttemptIdToTheAdmin() {
        var request = new SearchCourseGradesRequest();
        request.setKeyword("00001");
        request.setSemesterId(7L);
        when(students.findRosterStudentIdsByKeyword("00001")).thenReturn(Set.of(1L));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstEnrollment), PageRequest.of(0, 20), 1));
        var response = service.findCourseGrades(principal, request);
        assertThat(response.getContent())
                .extracting(grade -> grade.enrollmentId())
                .containsExactly(10L);
        assertThat(response.getContent().getFirst().studentCode()).isEqualTo("00001");
        var order = inOrder(students, repository);
        order.verify(students).findRosterStudentIdsByKeyword("00001");
        order.verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void importsByStudentCodeRegardlessOfFileOrderAndLeavesOtherAttemptsUntouched() {
        when(spreadsheet.read(null, moduleClass))
                .thenReturn(new ImportedCourseGrades(
                        List.of(new ImportedCourseGrade(6, "00002", new BigDecimal("0"))), List.of()));
        var response = service.importCourseGrades(principal, 100L, new ImportCourseGradesRequest(null));
        assertThat(response).extracting(grade -> grade.enrollmentId()).containsExactly(11L);
        assertThat(secondEnrollment.getStatus()).isEqualTo(EnrollmentStatus.FAILED);
        assertThat(firstEnrollment.getFinalExamScore()).isNull();
    }

    @Test
    void rejectsTheWholeImportBeforeMutationWhenAnyRowIsInvalidOrDuplicated() {
        when(spreadsheet.read(null, moduleClass))
                .thenReturn(new ImportedCourseGrades(
                        List.of(
                                new ImportedCourseGrade(6, "00001", new BigDecimal("9")),
                                new ImportedCourseGrade(9, "00001", new BigDecimal("7")),
                                new ImportedCourseGrade(10, "OTHER", new BigDecimal("8"))),
                        List.of()));
        assertThatThrownBy(() -> service.importCourseGrades(principal, 100L, new ImportCourseGradesRequest(null)))
                .isInstanceOfSatisfying(EnrollmentException.class, exception -> assertThat(exception.getErrors())
                        .extracting(FieldErrorDetail::getField)
                        .contains("rows[9].studentCode", "rows[10].studentCode"));
        assertThat(firstEnrollment.getFinalExamScore()).isNull();
        verify(repository, never()).flush();
    }

    @Test
    void refusesAmbiguousLearningAttemptsRatherThanChoosingTheMostRecentOne() {
        var repeatedAttempt = enrollment(12L, 1L);
        when(repository.findAllByModuleClassIdAndDeletedFalseAndStatusInOrderByIdAsc(eq(100L), anyCollection()))
                .thenReturn(List.of(firstEnrollment, repeatedAttempt));
        when(spreadsheet.read(null, moduleClass))
                .thenReturn(new ImportedCourseGrades(
                        List.of(new ImportedCourseGrade(6, "00001", new BigDecimal("9"))), List.of()));
        assertError(
                () -> service.importCourseGrades(principal, 100L, new ImportCourseGradesRequest(null)),
                ErrorCode.COURSE_GRADE_IMPORT_INVALID);
        verify(repository, never()).flush();
    }

    @Test
    void requiresProcessScoreAndPreservesTheAttemptWhenConversionIsNotConfigured() {
        settings.setGradeScale(List.of());
        assertError(
                () -> service.updateCourseGrade(principal, 10L, request("7")),
                ErrorCode.COURSE_GRADE_CONFIGURATION_INVALID);
        assertThat(firstEnrollment.getFinalExamScore()).isNull();
        var withoutProcess = Enrollment.builder()
                .moduleClassId(100L)
                .status(EnrollmentStatus.REGISTERED)
                .build();
        when(repository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(withoutProcess));
        assertError(() -> service.updateCourseGrade(principal, 10L, request("7")), ErrorCode.INVALID_SCORE);
        verify(repository, never()).flush();
    }

    private Enrollment enrollment(long enrollmentId, long studentId) {
        var enrollment = Enrollment.builder()
                .studentId(studentId)
                .moduleClassId(100L)
                .semesterId(7L)
                .subjectId(20L)
                .subjectCode("CS20")
                .subjectName("Học phần")
                .moduleClassCode("MC_100")
                .academicYearStart(2026)
                .semesterNumber(1)
                .credits(3)
                .registeredAt(currentBusinessTime.minusMonths(1))
                .componentScore1(new BigDecimal("7"))
                .componentScore2(new BigDecimal("7"))
                .processScore(new BigDecimal("7"))
                .status(EnrollmentStatus.REGISTERED)
                .build();
        enrollment.setId(enrollmentId);
        return enrollment;
    }

    @Test
    void aLockedRowPreventsWritingOtherValidRowsInTheSameImport() {
        firstEnrollment.updateCourseGrade(
                new BigDecimal("9"),
                new BigDecimal("8.4"),
                "B",
                new BigDecimal("3"),
                EnrollmentStatus.COMPLETED,
                currentBusinessTime.minusDays(14),
                currentBusinessTime,
                currentBusinessTime.minusDays(14));
        when(spreadsheet.read(null, moduleClass))
                .thenReturn(new ImportedCourseGrades(
                        List.of(
                                new ImportedCourseGrade(6, "00001", new BigDecimal("7")),
                                new ImportedCourseGrade(7, "00002", new BigDecimal("9"))),
                        List.of()));
        assertError(
                () -> service.importCourseGrades(principal, 100L, new ImportCourseGradesRequest(null)),
                ErrorCode.COURSE_GRADE_IMPORT_INVALID);
        assertThat(secondEnrollment.getFinalExamScore()).isNull();
        assertThat(firstEnrollment.getFinalExamScore()).isEqualByComparingTo("9");
        verify(repository, never()).flush();
    }

    @Test
    void refusesADeadlineThatExpiresWhileTheRequestIsBeingValidated() {
        firstEnrollment.updateCourseGrade(
                new BigDecimal("9"),
                new BigDecimal("8.4"),
                "B",
                new BigDecimal("3"),
                EnrollmentStatus.COMPLETED,
                currentBusinessTime.minusDays(14).plusSeconds(1),
                currentBusinessTime.plusSeconds(1),
                currentBusinessTime.minusDays(14));
        var advancingClock = mock(Clock.class);
        when(advancingClock.getZone()).thenReturn(clock.getZone());
        when(advancingClock.instant())
                .thenReturn(clock.instant(), clock.instant().plusSeconds(1));
        var serviceWithAdvancingClock = new CourseGradeService(
                repository, identity, students, scheduling, spreadsheet, settings, transactions, advancingClock);
        assertError(
                () -> serviceWithAdvancingClock.updateCourseGrade(principal, 10L, request("7")),
                ErrorCode.GRADE_LOCKED);
        assertThat(firstEnrollment.getFinalExamScore()).isEqualByComparingTo("9");
        verify(repository, never()).flush();
    }

    @Test
    void usesTheConfiguredWindowForBothIndividualAndBulkPublication() {
        settings.setGradeCorrectionWindow(Duration.ofHours(36));
        var individual = service.updateCourseGrade(principal, 10L, request("9"));
        assertThat(individual.gradeLockAt()).isEqualTo(currentBusinessTime.plusHours(36));
        when(spreadsheet.read(null, moduleClass))
                .thenReturn(new ImportedCourseGrades(
                        List.of(new ImportedCourseGrade(6, "00002", new BigDecimal("9"))), List.of()));
        var imported = service.importCourseGrades(principal, 100L, new ImportCourseGradesRequest(null));
        assertThat(imported.getFirst().gradeLockAt()).isEqualTo(currentBusinessTime.plusHours(36));
    }

    @Test
    void aZeroWindowPublishesAndReturnsAnImmediatelyLockedGrade() {
        settings.setGradeCorrectionWindow(Duration.ZERO);
        var response = service.updateCourseGrade(principal, 10L, request("9"));
        assertThat(response.gradeLockAt()).isEqualTo(response.gradePublishedAt());
        assertThat(response.gradeLocked()).isTrue();
    }

    @Test
    void anInvalidWindowDoesNotWriteOrPublishAnyGrade() {
        settings.setGradeCorrectionWindow(Duration.ofDays(-1));
        assertError(
                () -> service.updateCourseGrade(principal, 10L, request("9")),
                ErrorCode.COURSE_GRADE_LOCK_CONFIGURATION_INVALID);
        assertThat(firstEnrollment.getFinalExamScore()).isNull();
        assertThat(firstEnrollment.getGradePublishedAt()).isNull();
        verify(repository, never()).flush();
    }

    private UpdateCourseGradeRequest request(String examScore) {
        var request = new UpdateCourseGradeRequest();
        request.setFinalExamScore(new BigDecimal(examScore));
        return request;
    }

    private GradeBand band(String threshold, String letter, String point) {
        return new GradeBand(new BigDecimal(threshold), letter, new BigDecimal(point));
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(EnrollmentException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expected));
    }
}
