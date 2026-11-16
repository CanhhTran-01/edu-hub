package com.eduhub.edu_hub.enrollment.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.enrollment.api.dto.request.*;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.enrollment.infrastructure.spreadsheet.ProcessScoreSpreadsheet;
import com.eduhub.edu_hub.enrollment.infrastructure.spreadsheet.ProcessScoreSpreadsheet.ImportedProcessScore;
import com.eduhub.edu_hub.enrollment.infrastructure.spreadsheet.ProcessScoreSpreadsheet.ImportedProcessScores;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ProcessScoreServiceTest {
    private final EnrollmentRepository repository = mock(EnrollmentRepository.class);
    private final IdentityFacade identity = mock(IdentityFacade.class);
    private final PeopleFacade.Scheduling lecturers = mock(PeopleFacade.Scheduling.class);
    private final PeopleFacade.Enrollment students = mock(PeopleFacade.Enrollment.class);
    private final SchedulingFacade.Grading scheduling = mock(SchedulingFacade.Grading.class);
    private final ProcessScoreSpreadsheet spreadsheet = mock(ProcessScoreSpreadsheet.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ProcessScoreService service = new ProcessScoreService(
            repository, identity, lecturers, students, scheduling, spreadsheet, transactions, clock);
    private final Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("lecturer")
            .claim("token_use", "ACCESS")
            .build();
    private final Enrollment firstEnrollment = enrollment(10L, 1L);
    private final Enrollment secondEnrollment = enrollment(11L, 2L);

    @BeforeEach
    void setup() {
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(identity.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).role("LECTURER").build());
        when(lecturers.findLecturerIdByUserId(7L)).thenReturn(Optional.of(70L));
        when(scheduling.findModuleClassForGrading(100L)).thenReturn(moduleClass(ModuleClassStatus.CLOSED, 70L));
        when(scheduling.lockModuleClassForGrading(100L)).thenReturn(moduleClass(ModuleClassStatus.CLOSED, 70L));
        when(repository.findAllByModuleClassIdAndDeletedFalseAndStatusInOrderByIdAsc(
                        100L, Set.of(EnrollmentStatus.REGISTERED)))
                .thenReturn(List.of(firstEnrollment, secondEnrollment));
        when(students.findRosterStudentsByIds(Set.of(1L, 2L)))
                .thenReturn(List.of(
                        new StudentRosterProfileResponse(1L, "00001", "Nguyễn Văn Một"),
                        new StudentRosterProfileResponse(2L, "00002", "Nguyễn Văn Hai")));
    }

    @Test
    void mergesOnlyPresentCellsAndKeepsExamScoresAndAttemptStatus() {
        var patch = patch(10L);
        patch.setComponentScore2(new BigDecimal("8.1"));
        var response = service.updateScores(principal, 100L, batch(patch));

        assertThat(firstEnrollment.getComponentScore1()).isEqualByComparingTo("7.5");
        assertThat(firstEnrollment.getComponentScore2()).isEqualByComparingTo("8.1");
        assertThat(firstEnrollment.getProcessScore()).isEqualByComparingTo("7.9");
        assertThat(firstEnrollment.getFinalExamScore()).isEqualByComparingTo("9");
        assertThat(firstEnrollment.getCourseScore10()).isNull();
        assertThat(firstEnrollment.getStatus()).isEqualTo(EnrollmentStatus.REGISTERED);
        assertThat(firstEnrollment.getNote()).isEqualTo("Ghi chú cũ");
        assertThat(firstEnrollment.getUpdatedAt()).isEqualTo(LocalDateTime.parse("2026-11-16T03:00:00"));
        assertThat(response.students().getFirst().processScore()).isEqualByComparingTo("7.9");
        var order = inOrder(scheduling, repository);
        order.verify(scheduling).lockModuleClassForGrading(100L);
        order.verify(repository)
                .findAllByModuleClassIdAndDeletedFalseAndStatusInOrderByIdAsc(
                        100L, Set.of(EnrollmentStatus.REGISTERED));
    }

    @Test
    void explicitNullClearsOnlyThatComponentAndInvalidatesTheOldProcessScore() {
        var patch = patch(10L);
        patch.setComponentScore1(null);
        patch.setNote(null);
        service.updateScores(principal, 100L, batch(patch));
        assertThat(firstEnrollment.getComponentScore1()).isNull();
        assertThat(firstEnrollment.getComponentScore2()).isEqualByComparingTo("8");
        assertThat(firstEnrollment.getProcessScore()).isNull();
        assertThat(firstEnrollment.getNote()).isEmpty();
    }

    @Test
    void supportsNoteOnlyAndDoesNotChangeScores() {
        var patch = patch(10L);
        patch.setNote("  Đã kiểm tra  ");
        service.updateScores(principal, 100L, batch(patch));
        assertThat(firstEnrollment.getNote()).isEqualTo("Đã kiểm tra");
        assertThat(firstEnrollment.getComponentScore1()).isEqualByComparingTo("7.5");
        assertThat(firstEnrollment.getProcessScore()).isEqualByComparingTo("7.9");
    }

    @Test
    void equivalentNumericValuesDoNotChangeTheTimestamp() {
        var patch = patch(10L);
        patch.setComponentScore1(new BigDecimal("7.500"));
        service.updateScores(principal, 100L, batch(patch));
        assertThat(firstEnrollment.getUpdatedAt()).isNull();
    }

    @Test
    void rejectsAnEntireBatchBeforeMutatingAnyManagedEnrollment() {
        var validPatch = patch(10L);
        validPatch.setComponentScore1(BigDecimal.ZERO);
        var invalidPatch = patch(11L);
        invalidPatch.setComponentScore2(new BigDecimal("10.1"));
        invalidPatch.setNote("x".repeat(501));

        assertThatThrownBy(() -> service.updateScores(principal, 100L, batch(validPatch, invalidPatch)))
                .isInstanceOfSatisfying(EnrollmentException.class, exception -> {
                    assertThat(exception.getErrors())
                            .extracting(FieldErrorDetail::getField)
                            .containsExactly("students[1].componentScore2", "students[1].note");
                });
        assertThat(firstEnrollment.getComponentScore1()).isEqualByComparingTo("7.5");
        verify(repository, never()).flush();
        verify(transactions).rollback(any());
    }

    @Test
    void rejectsForeignAndDuplicateEnrollmentIdsTogether() {
        assertThatThrownBy(() -> service.updateScores(principal, 100L, batch(patch(999L), patch(10L), patch(10L))))
                .isInstanceOfSatisfying(EnrollmentException.class, exception -> assertThat(exception.getErrors())
                        .extracting(FieldErrorDetail::getField)
                        .containsExactly("students[0].enrollmentId", "students[2].enrollmentId"));
        verify(repository, never()).flush();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "ACADEMIC_ADMIN", "SYSTEM_ADMIN"})
    void rejectsOtherRolesEvenWhenTheyHaveAnAccessToken(String role) {
        when(identity.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).role(role).build());
        assertError(() -> service.updateScores(principal, 100L, batch(patch(10L))), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(scheduling, repository);
    }

    @Test
    void rejectsNullPrincipalAndFirstLoginTokenInTheService() {
        assertError(() -> service.findScores(null, 100L), ErrorCode.UNAUTHENTICATED);
        var restrictedJwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("lecturer")
                .claim("token_use", "PASSWORD_CHANGE")
                .build();
        assertError(() -> service.findScores(restrictedJwt, 100L), ErrorCode.FIRST_LOGIN_REQUIRED);
        verifyNoInteractions(identity);
    }

    @Test
    void rejectsMissingLecturerProfileInsteadOfBroadeningScope() {
        when(lecturers.findLecturerIdByUserId(7L)).thenReturn(Optional.empty());
        assertError(() -> service.findScores(principal, 100L), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(scheduling, repository);
    }

    @Test
    void checksAssignmentAgainAfterAcquiringTheClassLock() {
        when(scheduling.lockModuleClassForGrading(100L)).thenReturn(moduleClass(ModuleClassStatus.CLOSED, 71L));
        assertError(() -> service.updateScores(principal, 100L, batch(patch(10L))), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @EnumSource(
            value = ModuleClassStatus.class,
            names = {"OPEN", "SCHEDULED", "UNSCHEDULED", "CANCELLED"})
    void rejectsClassesThatHaveNotReachedTheGradingStage(ModuleClassStatus status) {
        when(scheduling.lockModuleClassForGrading(100L)).thenReturn(moduleClass(status, 70L));
        assertError(
                () -> service.updateScores(principal, 100L, batch(patch(10L))),
                ErrorCode.PROCESS_SCORE_CLASS_NOT_READY);
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsCompletedTeachingWithoutCompletingTheStudentAttempt() {
        when(scheduling.lockModuleClassForGrading(100L)).thenReturn(moduleClass(ModuleClassStatus.COMPLETED, 70L));
        service.updateScores(principal, 100L, batch(patch(10L)));
        assertThat(firstEnrollment.getStatus()).isEqualTo(EnrollmentStatus.REGISTERED);
    }

    @Test
    void importMatchesStudentCodesAndKeepsUnlistedStudentsAndBlankNotes() {
        when(spreadsheet.read(any(), any()))
                .thenReturn(new ImportedProcessScores(
                        List.of(new ImportedProcessScore(6, "00001", BigDecimal.ZERO, BigDecimal.TEN, "")), List.of()));
        service.importScores(principal, 100L, new ImportProcessScoresRequest(null));
        assertThat(firstEnrollment.getComponentScore1()).isEqualByComparingTo("0");
        assertThat(firstEnrollment.getProcessScore()).isEqualByComparingTo("7");
        assertThat(firstEnrollment.getNote()).isEqualTo("Ghi chú cũ");
        assertThat(secondEnrollment.getComponentScore1()).isEqualByComparingTo("7.5");
    }

    @Test
    void importAggregatesParserScoreMembershipAndDuplicateErrorsWithoutUpdatingValidRows() {
        when(spreadsheet.read(any(), any()))
                .thenReturn(new ImportedProcessScores(
                        List.of(
                                new ImportedProcessScore(6, "00001", BigDecimal.ZERO, BigDecimal.TEN, null),
                                new ImportedProcessScore(8, "00001", new BigDecimal("11"), null, null),
                                new ImportedProcessScore(9, "UNKNOWN", BigDecimal.ONE, BigDecimal.ONE, null)),
                        List.of(new FieldErrorDetail("rows[8].componentScore2", "Thiếu TP2"))));
        assertThatThrownBy(() -> service.importScores(principal, 100L, new ImportProcessScoresRequest(null)))
                .isInstanceOfSatisfying(EnrollmentException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROCESS_SCORE_IMPORT_INVALID);
                    assertThat(exception.getErrors())
                            .extracting(FieldErrorDetail::getField)
                            .containsExactly(
                                    "rows[8].componentScore2",
                                    "rows[8].studentCode",
                                    "rows[8].componentScore1",
                                    "rows[9].studentCode");
                });
        assertThat(firstEnrollment.getComponentScore1()).isEqualByComparingTo("7.5");
        verify(repository, never()).flush();
    }

    @Test
    void importRechecksAssignmentAfterReadingTheFile() {
        when(spreadsheet.read(any(), any()))
                .thenReturn(new ImportedProcessScores(
                        List.of(new ImportedProcessScore(6, "00001", BigDecimal.ONE, BigDecimal.ONE, null)),
                        List.of()));
        when(scheduling.lockModuleClassForGrading(100L)).thenReturn(moduleClass(ModuleClassStatus.CLOSED, 71L));
        assertError(
                () -> service.importScores(principal, 100L, new ImportProcessScoresRequest(null)),
                ErrorCode.UNAUTHORIZED);
        assertThat(firstEnrollment.getComponentScore1()).isEqualByComparingTo("7.5");
        verify(repository, never()).flush();
    }

    private Enrollment enrollment(Long enrollmentId, Long studentId) {
        var enrollment = Enrollment.builder()
                .moduleClassId(100L)
                .studentId(studentId)
                .status(EnrollmentStatus.REGISTERED)
                .componentScore1(new BigDecimal("7.5"))
                .componentScore2(new BigDecimal("8"))
                .processScore(new BigDecimal("7.9"))
                .finalExamScore(new BigDecimal("9"))
                .note("Ghi chú cũ")
                .build();
        enrollment.setId(enrollmentId);
        return enrollment;
    }

    private ModuleClassGradingResponse moduleClass(ModuleClassStatus status, Long lecturerId) {
        return new ModuleClassGradingResponse(100L, "MC_100", 1L, "HK_1_2026_2027", lecturerId, status);
    }

    private UpdateProcessScoreRequest patch(Long enrollmentId) {
        var patch = new UpdateProcessScoreRequest();
        patch.setEnrollmentId(enrollmentId);
        return patch;
    }

    private UpdateProcessScoresRequest batch(UpdateProcessScoreRequest... patches) {
        var request = new UpdateProcessScoresRequest();
        request.setStudents(Arrays.asList(patches));
        return request;
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(EnrollmentException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode));
    }
}
