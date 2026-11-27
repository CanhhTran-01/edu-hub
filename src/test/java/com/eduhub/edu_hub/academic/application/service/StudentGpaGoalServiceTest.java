package com.eduhub.edu_hub.academic.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest;
import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest.CourseGradeConditionRequest;
import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationPreviewResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationSnapshotResponse;
import com.eduhub.edu_hub.academic.application.exception.GpaSimulationException;
import com.eduhub.edu_hub.academic.domain.enums.*;
import com.eduhub.edu_hub.academic.domain.policy.GpaSimulationPolicy;
import com.eduhub.edu_hub.academic.infrastructure.config.GpaGoalSolverSettings;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class StudentGpaGoalServiceTest {
    StudentGpaSimulationService simulation = mock(StudentGpaSimulationService.class);
    GpaGoalSolverSettings settings = new GpaGoalSolverSettings();
    Clock clock = Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    StudentGpaGoalService service = new StudentGpaGoalService(simulation, settings, clock);
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .build();
    String reference = "a".repeat(64);
    GpaSimulationSnapshotResponse snapshot;

    @BeforeEach
    void prepare() {
        snapshot = snapshot(
                decimal("3"),
                List.of(
                        course(1L, "X", "Môn X", null),
                        course(2L, "Y", "Môn Y", null),
                        course(3L, "Z", "Môn Z", null)));
        when(simulation.findMySnapshot(principal)).thenAnswer(invocation -> snapshot);
        when(simulation.previewMyGpa(eq(principal), any()))
                .thenAnswer(invocation -> preview(invocation.getArgument(1)));
    }

    @Test
    void multipleExactConditionsAreKeptWhileOtherCoursesAreSolvedAndVerified() {
        var solution = service.solveMyGpaGoal(
                principal,
                request(
                        List.of(),
                        List.of(
                                condition("X", CourseGradeComparison.EXACT, "B"),
                                condition("Y", CourseGradeComparison.EXACT, "C"))));
        assertThat(solution.status()).isEqualTo(GpaGoalStatus.SOLVED);
        assertThat(solution.conditions()).hasSize(2);
        assertThat(solution.preview().courses().stream().map(GpaSimulationPreviewResponse.CourseResponse::grade4))
                .containsExactly(decimal("3"), decimal("2"), decimal("4"));
        assertThat(solution.preview().semesterGpa()).isEqualByComparingTo("3.00");
        verify(simulation).previewMyGpa(eq(principal), any(GpaSimulationRequest.class));
    }

    @Test
    void maximumGradeIsDifferentFromAnExactGrade() {
        snapshot = snapshot(BigDecimal.ONE, List.of(course(1L, "X", "Môn X", null)));
        var maximum = service.solveMyGpaGoal(
                principal, request(List.of(), List.of(condition("X", CourseGradeComparison.AT_MOST, "C"))));
        var exact = service.solveMyGpaGoal(
                principal, request(List.of(), List.of(condition("X", CourseGradeComparison.EXACT, "C"))));
        assertThat(maximum.preview().courses().getFirst().grade4()).isEqualByComparingTo("1");
        assertThat(exact.preview().courses().getFirst().grade4()).isEqualByComparingTo("2");
    }

    @Test
    void changingOneCoursePreservesOtherUserConditionsAndDoesNotFreezeEarlierProposals() {
        var solution = service.solveMyGpaGoal(
                principal,
                request(
                        List.of(
                                condition("X", CourseGradeComparison.EXACT, "B"),
                                condition("Y", CourseGradeComparison.EXACT, "C")),
                        List.of(condition("X", CourseGradeComparison.AT_MOST, "B"))));
        assertThat(solution.conditions())
                .extracting(CourseGradeConditionRequest::comparison)
                .containsExactly(CourseGradeComparison.AT_MOST, CourseGradeComparison.EXACT);
        assertThat(solution.preview().courses().get(1).grade4()).isEqualByComparingTo("2");
    }

    @Test
    void removingAConditionCanRestoreFeasibility() {
        snapshot = snapshot(decimal("2"), List.of(course(1L, "X", "Môn X", null)));
        var oldCondition = condition("X", CourseGradeComparison.EXACT, "F");
        var blocked = service.solveMyGpaGoal(principal, request(List.of(oldCondition), List.of()));
        assertThat(blocked.status()).isEqualTo(GpaGoalStatus.INFEASIBLE);
        var restored = service.solveMyGpaGoal(
                principal, request(List.of(oldCondition), List.of(condition("X", CourseGradeComparison.REMOVE, null))));
        assertThat(restored.status()).isEqualTo(GpaGoalStatus.SOLVED);
        assertThat(restored.conditions()).isEmpty();
    }

    @Test
    void ambiguousCourseAndUnspecifiedComparisonAskInsteadOfGuessing() {
        snapshot = snapshot(
                decimal("3"),
                List.of(course(1L, "JAVA1", "Java cơ bản", null), course(2L, "JAVA2", "Java nâng cao", null)));
        var ambiguous = service.solveMyGpaGoal(
                principal, request(List.of(), List.of(condition("Java", CourseGradeComparison.EXACT, "B"))));
        assertThat(ambiguous.status()).isEqualTo(GpaGoalStatus.NEEDS_CLARIFICATION);
        assertThat(ambiguous.message()).contains("JAVA1", "JAVA2");
        var unspecified = service.solveMyGpaGoal(
                principal, request(List.of(), List.of(condition("JAVA1", CourseGradeComparison.UNSPECIFIED, "B"))));
        assertThat(unspecified.message()).contains("đúng B", "tối đa", "trở lên");
        verify(simulation, never()).previewMyGpa(any(), any());
    }

    @Test
    void contradictoryConditionsInOneMessageAreNotSilentlyOverwritten() {
        var solution = service.solveMyGpaGoal(
                principal,
                request(
                        List.of(),
                        List.of(
                                condition("X", CourseGradeComparison.EXACT, "B"),
                                condition("X", CourseGradeComparison.AT_MOST, "C"))));
        assertThat(solution.status()).isEqualTo(GpaGoalStatus.NEEDS_CLARIFICATION);
        assertThat(solution.pendingConditions()).hasSize(2);
        verify(simulation, never()).previewMyGpa(any(), any());
    }

    @Test
    void publishedGradeIsFixedEvenWhenUserRequestsAnotherExactGrade() {
        snapshot = snapshot(decimal("3"), List.of(course(1L, "X", "Môn X", decimal("3"))));
        var solution = service.solveMyGpaGoal(
                principal, request(List.of(), List.of(condition("X", CourseGradeComparison.EXACT, "C"))));
        assertThat(solution.status()).isEqualTo(GpaGoalStatus.NEEDS_CLARIFICATION);
        assertThat(solution.message()).contains("Điểm đã công bố");
        verify(simulation, never()).previewMyGpa(any(), any());
    }

    @Test
    void missingSemesterTargetDirectsToProfileAndDoesNotUseGraduationTarget() {
        snapshot = snapshot(null, snapshot.courses());
        var solution = service.solveMyGpaGoal(principal, request(List.of(), List.of()));
        assertThat(solution.status()).isEqualTo(GpaGoalStatus.TARGET_NOT_SET);
        assertThat(solution.message()).contains("Profile");
        verify(simulation, never()).previewMyGpa(any(), any());
    }

    @Test
    void zeroSemesterTargetIsConfiguredAndCanBeSolved() {
        snapshot = snapshot(BigDecimal.ZERO, snapshot.courses());
        assertThat(service.solveMyGpaGoal(principal, request(List.of(), List.of()))
                        .status())
                .isEqualTo(GpaGoalStatus.SOLVED);
    }

    @Test
    void changedSourceAtFinalVerificationCannotBecomeASavedSolution() {
        when(simulation.previewMyGpa(eq(principal), any()))
                .thenThrow(new GpaSimulationException(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED));
        assertThatThrownBy(() -> service.solveMyGpaGoal(principal, request(List.of(), List.of())))
                .isInstanceOfSatisfying(GpaSimulationException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED));
    }

    @Test
    void nullPrincipalIsRejectedBeforeReadingAnyStudentData() {
        assertThatThrownBy(() -> service.solveMyGpaGoal(null, request(List.of(), List.of())))
                .isInstanceOfSatisfying(GpaSimulationException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(simulation);
    }

    private GpaGoalRequest request(
            List<CourseGradeConditionRequest> existing, List<CourseGradeConditionRequest> changes) {
        return new GpaGoalRequest(reference, existing, changes);
    }

    private CourseGradeConditionRequest condition(String course, CourseGradeComparison comparison, String letter) {
        return new CourseGradeConditionRequest(course, null, comparison, letter);
    }

    private GpaSimulationSnapshotResponse.CourseResponse course(
            Long enrollmentId, String code, String name, BigDecimal published) {
        return new GpaSimulationSnapshotResponse.CourseResponse(
                enrollmentId,
                enrollmentId,
                code,
                name,
                3,
                published == null,
                published,
                published == null ? null : "B");
    }

    private GpaSimulationSnapshotResponse snapshot(
            BigDecimal target, List<GpaSimulationSnapshotResponse.CourseResponse> courses) {
        return GpaSimulationSnapshotResponse.builder()
                .snapshotRef(reference)
                .observedAt(LocalDateTime.now(clock))
                .semester(SemesterResponse.builder()
                        .id(2L)
                        .semesterName("Kỳ hiện tại")
                        .build())
                .historyComplete(true)
                .scopeNotice("Đủ lịch sử")
                .graduationTargetGpa(decimal("3.20"))
                .semesterTargetGpa(target)
                .courses(courses)
                .gradeOptions(List.of(
                        option("0", "F"), option("1", "D"), option("2", "C"), option("3", "B"), option("4", "A")))
                .build();
    }

    private GpaSimulationSnapshotResponse.GradeOptionResponse option(String points, String letter) {
        return new GpaSimulationSnapshotResponse.GradeOptionResponse(decimal(points), List.of(letter));
    }

    private GpaSimulationPreviewResponse preview(GpaSimulationRequest request) {
        var assumptions = request.assumptions().stream()
                .collect(Collectors.toMap(
                        GpaSimulationRequest.CourseAssumptionRequest::enrollmentId,
                        GpaSimulationRequest.CourseAssumptionRequest::grade4));
        var attempts = snapshot.courses().stream()
                .map(course -> new GpaSimulationPolicy.Attempt(
                        course.enrollmentId(),
                        course.subjectId(),
                        2L,
                        course.subjectCode(),
                        course.subjectName(),
                        course.credits(),
                        2026,
                        1,
                        LocalDateTime.now(clock),
                        course.actualGrade4(),
                        course.actualGradeLetter(),
                        course.actualGrade4() != null))
                .toList();
        var calculation = GpaSimulationPolicy.simulate(
                attempts,
                2L,
                assumptions,
                snapshot.gradeOptions().stream()
                        .map(GpaSimulationSnapshotResponse.GradeOptionResponse::grade4)
                        .toList(),
                true);
        return GpaSimulationPreviewResponse.builder()
                .snapshotRef(reference)
                .semesterId(2L)
                .calculatedAt(LocalDateTime.now(clock))
                .semesterGpa(calculation.semesterGpa())
                .cumulativeGpa(calculation.cumulativeGpa())
                .historyComplete(true)
                .semesterTargetGpa(snapshot.semesterTargetGpa())
                .courses(snapshot.courses().stream()
                        .map(course -> new GpaSimulationPreviewResponse.CourseResponse(
                                course.enrollmentId(),
                                course.subjectCode(),
                                course.subjectName(),
                                course.credits(),
                                !course.canAdjustGrade(),
                                course.canAdjustGrade()
                                        ? assumptions.get(course.enrollmentId())
                                        : course.actualGrade4(),
                                List.of()))
                        .toList())
                .build();
    }

    private BigDecimal decimal(String number) {
        return new BigDecimal(number);
    }
}
