package com.eduhub.edu_hub.academic.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest;
import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest.CourseAssumptionRequest;
import com.eduhub.edu_hub.academic.application.exception.GpaSimulationException;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.enrollment.api.dto.response.CourseGradeScaleResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.CourseGradeScaleResponse.GradeBandResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse.LearningRecordResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.response.StudentAcademicProfileResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.enums.StudentActivityStatus;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

class StudentGpaSimulationServiceTest {
    IdentityFacade identity = mock(IdentityFacade.class);
    PeopleFacade.Academic people = mock(PeopleFacade.Academic.class);
    EnrollmentFacade.StudentRecords records = mock(EnrollmentFacade.StudentRecords.class);
    EnrollmentFacade.GradeScale scale = mock(EnrollmentFacade.GradeScale.class);
    CurriculumFacade.Academic curriculum = mock(CurriculumFacade.Academic.class);
    SchedulingFacade.AcademicCalendar calendar = mock(SchedulingFacade.AcademicCalendar.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    StudentGpaSimulationService service = createService(clock);
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .claim("token_use", "ACCESS")
            .build();
    SemesterResponse previous = semester(1, 2025);
    SemesterResponse current = semester(2, 2026);

    @BeforeEach
    void setup() {
        when(identity.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).role("STUDENT").build());
        when(people.findStudentByUserId(7L)).thenReturn(student(99));
        when(calendar.findCurrentSemester()).thenReturn(Optional.of(current));
        when(calendar.findSemestersByIds(anySet())).thenReturn(List.of(previous, current));
        when(curriculum.findCurriculum(3L, 4L, TrainingType.CHINH_QUY)).thenReturn(Optional.empty());
        when(scale.findCurrentGradeScale())
                .thenReturn(new CourseGradeScaleResponse(List.of(
                        new GradeBandResponse(BigDecimal.ZERO, "F", BigDecimal.ZERO),
                        new GradeBandResponse(new BigDecimal("4"), "D", BigDecimal.ONE),
                        new GradeBandResponse(new BigDecimal("5.5"), "C", new BigDecimal("2")),
                        new GradeBandResponse(new BigDecimal("7"), "B", new BigDecimal("3")),
                        new GradeBandResponse(new BigDecimal("8.5"), "A", new BigDecimal("4")),
                        new GradeBandResponse(new BigDecimal("9"), "A+", new BigDecimal("4.0")))));
        history(
                true,
                List.of(enrollment(10, 1, 1, 2, "0"), enrollment(11, 1, 2, 3, null), enrollment(12, 2, 2, 3, "3")));
    }

    @Test
    void snapshotKeepsPublishedLegacyGradesReadOnlyAndGroupsEqualGradePoints() {
        var snapshot = service.findMySnapshot(principal);
        assertThat(snapshot.snapshotRef()).matches("[0-9a-f]{64}");
        assertThat(snapshot.courses()).hasSize(2);
        assertThat(snapshot.courses().get(0).canAdjustGrade()).isTrue();
        assertThat(snapshot.courses().get(1).canAdjustGrade()).isFalse();
        assertThat(snapshot.courses().get(1).actualGrade4()).isEqualByComparingTo("3");
        assertThat(snapshot.gradeOptions()).hasSize(5);
        assertThat(snapshot.gradeOptions().getLast().letters()).containsExactly("A", "A+");
        assertThat(snapshot.defaultGrade4()).isEqualByComparingTo("2");
        assertThat(snapshot.semesterTargetGpa()).isEqualByComparingTo("3");
        assertThat(snapshot.graduationTargetGpa()).isEqualByComparingTo("3.2");
        verify(records).findLearningHistory(99L);
    }

    @Test
    void snapshotIdentityDoesNotChangeOnlyBecauseTheReadTimeChanges() {
        assertThat(createService(Clock.offset(clock, Duration.ofHours(1)))
                        .findMySnapshot(principal)
                        .snapshotRef())
                .isEqualTo(service.findMySnapshot(principal).snapshotRef());
    }

    @Test
    void previewReplacesTheOldFailureAndComparesTheRoundedSemesterGpa() {
        var snapshot = service.findMySnapshot(principal);
        var preview = service.previewMyGpa(principal, request(snapshot.snapshotRef(), "3"));
        assertThat(preview.semesterGpa()).isEqualByComparingTo("3.00");
        assertThat(preview.cumulativeGpa()).isEqualByComparingTo("3.00");
        assertThat(preview.accumulatedCredits()).isEqualTo(6);
        assertThat(preview.semesterCredits()).isEqualTo(6);
        assertThat(preview.meetsSemesterTarget()).isTrue();
        assertThat(preview.semesterTargetGap()).isEqualByComparingTo("0");
        assertThat(preview.courses().getFirst().hasPublishedGrade()).isFalse();
    }

    @Test
    void publicationAfterOpeningThePopupInvalidatesItsSnapshot() {
        String reference = service.findMySnapshot(principal).snapshotRef();
        history(
                true,
                List.of(enrollment(10, 1, 1, 2, "0"), enrollment(11, 1, 2, 3, "4"), enrollment(12, 2, 2, 3, "3")));
        assertThatThrownBy(() -> service.previewMyGpa(principal, request(reference, "3")))
                .isInstanceOfSatisfying(GpaSimulationException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED));
    }

    @Test
    void changedGradeScaleInvalidatesTheSnapshotEvenIfTheSubmittedPointStillExists() {
        String reference = service.findMySnapshot(principal).snapshotRef();
        when(scale.findCurrentGradeScale())
                .thenReturn(new CourseGradeScaleResponse(List.of(
                        new GradeBandResponse(BigDecimal.ZERO, "F", BigDecimal.ZERO),
                        new GradeBandResponse(new BigDecimal("4"), "D", BigDecimal.ONE),
                        new GradeBandResponse(new BigDecimal("7.1"), "B", new BigDecimal("3")))));
        assertThatThrownBy(() -> service.previewMyGpa(principal, request(reference, "3")))
                .isInstanceOfSatisfying(GpaSimulationException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED));
    }

    @Test
    void duplicateMissingPublishedAndForeignAttemptsAreRejected() {
        String reference = service.findMySnapshot(principal).snapshotRef();
        for (var assumptions : List.of(
                List.<CourseAssumptionRequest>of(),
                List.of(
                        new CourseAssumptionRequest(11L, BigDecimal.ONE),
                        new CourseAssumptionRequest(11L, BigDecimal.ONE)),
                List.of(new CourseAssumptionRequest(12L, BigDecimal.ONE)),
                List.of(new CourseAssumptionRequest(999L, BigDecimal.ONE)))) {
            assertThatThrownBy(() -> service.previewMyGpa(principal, new GpaSimulationRequest(reference, assumptions)))
                    .isInstanceOf(GpaSimulationException.class);
        }
    }

    @Test
    void missingHistoryPreservesUnknownCumulativeAndMissingTargetIsDifferentFromZero() {
        history(false, List.of(enrollment(11, 1, 2, 3, null)));
        when(people.findStudentByUserId(7L)).thenReturn(student(99, null));
        String reference = service.findMySnapshot(principal).snapshotRef();
        var withoutTarget = service.previewMyGpa(principal, request(reference, "0"));
        assertThat(withoutTarget.cumulativeGpa()).isNull();
        assertThat(withoutTarget.accumulatedCredits()).isNull();
        assertThat(withoutTarget.meetsSemesterTarget()).isNull();
        assertThat(withoutTarget.semesterTargetGpa()).isNull();
        assertThat(withoutTarget.graduationTargetGpa()).isEqualByComparingTo("3.2");
        when(people.findStudentByUserId(7L)).thenReturn(student(99, BigDecimal.ZERO));
        String zeroTargetReference = service.findMySnapshot(principal).snapshotRef();
        assertThat(service.previewMyGpa(principal, request(zeroTargetReference, "0"))
                        .meetsSemesterTarget())
                .isTrue();
    }

    @Test
    void currentSemesterPreviewDoesNotUseRetakesFromFutureSemesters() {
        when(calendar.findSemestersByIds(anySet())).thenReturn(List.of(previous, current, semester(3, 2027)));
        history(
                true,
                List.of(
                        enrollment(10, 1, 1, 2, "0"),
                        enrollment(11, 1, 2, 3, null),
                        enrollment(12, 2, 2, 3, "3"),
                        enrollment(13, 1, 3, 3, "4")));
        var snapshot = service.findMySnapshot(principal);
        assertThat(snapshot.semester().getId()).isEqualTo(2L);
        assertThat(snapshot.courses()).hasSize(2);
        var preview = service.previewMyGpa(principal, request(snapshot.snapshotRef(), "1"));
        assertThat(preview.semesterId()).isEqualTo(2L);
        assertThat(preview.semesterGpa()).isEqualByComparingTo("2");
        assertThat(preview.cumulativeGpa()).isEqualByComparingTo("2");
    }

    @Test
    void semesterRolloverRejectsThePreviousSnapshotBeforeApplyingOldAssumptions() {
        String oldReference = service.findMySnapshot(principal).snapshotRef();
        when(calendar.findCurrentSemester()).thenReturn(Optional.of(semester(3, 2027)));
        assertThatThrownBy(() -> service.previewMyGpa(principal, request(oldReference, "3")))
                .isInstanceOfSatisfying(GpaSimulationException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED));
    }

    @Test
    void missingCurrentSemesterDoesNotFallBackToAnOldSemester() {
        when(calendar.findCurrentSemester()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findMySnapshot(principal))
                .isInstanceOfSatisfying(GpaSimulationException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.previewMyGpa(principal, request("a".repeat(64), "3")))
                .isInstanceOf(GpaSimulationException.class);
        verifyNoInteractions(records);
        verify(calendar, never()).findSemester(anyLong());
    }

    @Test
    void snapshotIsBoundToTheStudentResolvedFromThePrincipal() {
        String reference = service.findMySnapshot(principal).snapshotRef();
        when(people.findStudentByUserId(7L)).thenReturn(student(100));
        var sameRecords = records.findLearningHistory(99L);
        when(records.findLearningHistory(100L)).thenReturn(sameRecords);
        assertThatThrownBy(() -> service.previewMyGpa(principal, request(reference, "3")))
                .isInstanceOfSatisfying(GpaSimulationException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED));
    }

    @Test
    void missingAndFirstLoginPrincipalsCannotReadASnapshot() {
        assertThatThrownBy(() -> service.findMySnapshot(null)).isInstanceOf(GpaSimulationException.class);
        var temporary = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("student")
                .claim("token_use", "PASSWORD_CHANGE")
                .build();
        assertThatThrownBy(() -> service.findMySnapshot(temporary)).isInstanceOf(GpaSimulationException.class);
        verifyNoInteractions(records);
    }

    @Test
    void changedProfileTargetInvalidatesSnapshotAndReloadUsesTheNewSavedTarget() {
        String oldReference = service.findMySnapshot(principal).snapshotRef();
        when(people.findStudentByUserId(7L)).thenReturn(student(99, new BigDecimal("3.5")));
        assertThatThrownBy(() -> service.previewMyGpa(principal, request(oldReference, "3")))
                .isInstanceOfSatisfying(GpaSimulationException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED));
        var refreshed = service.findMySnapshot(principal);
        var preview = service.previewMyGpa(principal, request(refreshed.snapshotRef(), "3"));
        assertThat(preview.semesterTargetGpa()).isEqualByComparingTo("3.50");
        assertThat(preview.semesterTargetGap()).isEqualByComparingTo("0.50");
        assertThat(preview.meetsSemesterTarget()).isFalse();
    }

    private StudentGpaSimulationService createService(Clock simulationClock) {
        return new StudentGpaSimulationService(
                identity,
                people,
                records,
                scale,
                curriculum,
                calendar,
                JsonMapper.builder().findAndAddModules().build(),
                simulationClock);
    }

    private GpaSimulationRequest request(String reference, String grade) {
        return new GpaSimulationRequest(reference, List.of(new CourseAssumptionRequest(11L, new BigDecimal(grade))));
    }

    private void history(boolean complete, List<LearningRecordResponse> enrollments) {
        when(records.findLearningHistory(99L))
                .thenReturn(
                        new StudentLearningHistoryResponse(complete, 2026, "Phạm vi khóa mới", Set.of(), enrollments));
    }

    private StudentAcademicProfileResponse student(long studentId) {
        return student(studentId, new BigDecimal("3"));
    }

    private StudentAcademicProfileResponse student(long studentId, BigDecimal semesterTargetGpa) {
        return new StudentAcademicProfileResponse(
                studentId,
                "PRIVATE_CODE",
                "PRIVATE_NAME",
                3L,
                "Ngành",
                4L,
                "K8",
                "CT8A",
                TrainingType.CHINH_QUY,
                2026,
                StudentActivityStatus.STUDYING,
                new BigDecimal("3.2"),
                semesterTargetGpa,
                null,
                null);
    }

    private SemesterResponse semester(long semesterId, int year) {
        return SemesterResponse.builder()
                .id(semesterId)
                .semesterCode("HK_1_" + year)
                .semesterName("Học kỳ " + year)
                .startDate(LocalDate.of(year, 8, 1))
                .endDate(LocalDate.of(year, 12, 31))
                .build();
    }

    private LearningRecordResponse enrollment(
            long enrollmentId, long subjectId, long semesterId, int credits, String grade) {
        int year = 2024 + (int) semesterId;
        return new LearningRecordResponse(
                enrollmentId,
                100L + enrollmentId,
                "MC" + enrollmentId,
                subjectId,
                "CS" + subjectId,
                "Học phần " + subjectId,
                semesterId,
                year,
                1,
                credits,
                LocalDateTime.of(year, 7, 1, 0, 0),
                grade == null
                        ? EnrollmentStatus.REGISTERED
                        : grade.equals("0") ? EnrollmentStatus.FAILED : EnrollmentStatus.COMPLETED,
                null,
                null,
                null,
                null,
                grade == null ? null : BigDecimal.TEN,
                grade == null ? null : "B",
                grade == null ? null : new BigDecimal(grade),
                null,
                null,
                grade != null);
    }
}
