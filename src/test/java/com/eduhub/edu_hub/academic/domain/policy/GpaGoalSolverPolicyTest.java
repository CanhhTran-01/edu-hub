package com.eduhub.edu_hub.academic.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.academic.domain.enums.GpaGoalStatus;
import com.eduhub.edu_hub.academic.domain.policy.GpaGoalSolverPolicy.Course;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.junit.jupiter.api.Test;

class GpaGoalSolverPolicyTest {
    List<BigDecimal> grades = List.of(decimal("0"), decimal("1"), decimal("2"), decimal("3"), decimal("4"));

    @Test
    void publishedFailureMakesSixEqualCreditCoursesUnableToReachThreePointFive() {
        var courses = new ArrayList<Course>();
        courses.add(new Course(1L, 3, BigDecimal.ZERO, grades));
        for (long enrollmentId = 2; enrollmentId <= 6; enrollmentId++)
            courses.add(new Course(enrollmentId, 3, null, grades));
        var solution = solve(courses, "3.50");
        assertThat(solution.status()).isEqualTo(GpaGoalStatus.INFEASIBLE);
        assertThat(solution.maximumPossibleGpa()).isEqualByComparingTo("3.33");
        assertThat(solution.assumptions()).doesNotContainKey(1L);
        assertThat(solution.optimal()).isTrue();
    }

    @Test
    void avoidsAnAdditionalFailureEvenWhenThereIsAlreadyAPublishedFailure() {
        var solution = solve(
                List.of(
                        new Course(1L, 3, BigDecimal.ZERO, grades),
                        new Course(2L, 3, null, grades),
                        new Course(3L, 3, null, grades)),
                "0");
        assertThat(solution.status()).isEqualTo(GpaGoalStatus.SOLVED);
        assertThat(solution.assumptions().values()).allMatch(grade -> grade.signum() > 0);
    }

    @Test
    void mandatoryFailureDoesNotIntroduceAnotherAvoidableFailure() {
        var solution = solve(
                List.of(new Course(1L, 3, null, List.of(BigDecimal.ZERO)), new Course(2L, 3, null, grades)), "1.00");
        assertThat(solution.status()).isEqualTo(GpaGoalStatus.SOLVED);
        assertThat(solution.assumptions().get(1L)).isEqualByComparingTo("0");
        assertThat(solution.assumptions().get(2L)).isEqualByComparingTo("2");
    }

    @Test
    void highPublishedGradeDoesNotRaiseTheCapOfTheAdjustableCourses() {
        var solution = solve(List.of(new Course(1L, 3, decimal("4"), grades), new Course(2L, 3, null, grades)), "2.50");
        assertThat(solution.assumptions().get(2L)).isEqualByComparingTo("1");
        assertThat(solution.optimal()).isTrue();
    }

    @Test
    void finalRoundingUsesTheSameTwoDigitHalfUpRuleAsAcademicGpa() {
        var solution =
                solve(List.of(new Course(1L, 199, decimal("2.99"), grades), new Course(2L, 1, null, grades)), "3.00");
        assertThat(solution.status()).isEqualTo(GpaGoalStatus.SOLVED);
        assertThat(solution.assumptions().get(2L)).isEqualByComparingTo("4");
        assertThat(solution.maximumPossibleGpa()).isEqualByComparingTo("3.00");
    }

    @Test
    void noBudgetIsDifferentFromProvenInfeasibility() {
        var solution =
                GpaGoalSolverPolicy.solve(List.of(new Course(1L, 3, null, grades)), decimal("3"), 100, () -> false);
        assertThat(solution.status()).isEqualTo(GpaGoalStatus.SEARCH_LIMIT_REACHED);
        assertThat(solution.maximumPossibleGpa()).isNull();
        assertThat(solution.assumptions()).isEmpty();
        assertThat(solution.optimal()).isFalse();
    }

    @Test
    void aValidatedCandidateSurvivesTheOptimizationBudgetLimit() {
        var solution = GpaGoalSolverPolicy.solve(List.of(new Course(1L, 3, null, grades)), decimal("2"), 1, () -> true);
        assertThat(solution.status()).isEqualTo(GpaGoalStatus.SOLVED);
        assertThat(solution.assumptions().get(1L)).isEqualByComparingTo("4");
        assertThat(solution.optimal()).isFalse();
        assertThat(solution.evaluatedAssignments()).isEqualTo(1);
    }

    @Test
    void minimaxMatchesIndependentExhaustiveEnumerationAcrossWeightedSmallProblems() {
        for (int firstCredits = 1; firstCredits <= 3; firstCredits++) {
            for (int secondCredits = 1; secondCredits <= 3; secondCredits++) {
                var courses = List.of(
                        new Course(1L, firstCredits, null, grades),
                        new Course(2L, secondCredits, null, List.of(decimal("0"), decimal("1"), decimal("2"))),
                        new Course(3L, 2, decimal("3"), grades));
                for (int targetHundredths = 0; targetHundredths <= 400; targetHundredths += 5) {
                    var target = BigDecimal.valueOf(targetHundredths, 2);
                    BigDecimal expectedCap = null;
                    for (var firstGrade : grades) {
                        for (var secondGrade : courses.get(1).allowedGrades()) {
                            if (firstGrade.signum() == 0 || secondGrade.signum() == 0) continue;
                            BigDecimal weightedTotal = firstGrade
                                    .multiply(BigDecimal.valueOf(firstCredits))
                                    .add(secondGrade.multiply(BigDecimal.valueOf(secondCredits)))
                                    .add(decimal("6"));
                            BigDecimal gpa = weightedTotal.divide(
                                    BigDecimal.valueOf(firstCredits + secondCredits + 2), 2, RoundingMode.HALF_UP);
                            if (gpa.compareTo(target) >= 0)
                                expectedCap = expectedCap == null
                                        ? firstGrade.max(secondGrade)
                                        : expectedCap.min(firstGrade.max(secondGrade));
                        }
                    }
                    var actual = GpaGoalSolverPolicy.solve(courses, target, 1000, () -> true);
                    if (expectedCap == null) assertThat(actual.status()).isEqualTo(GpaGoalStatus.INFEASIBLE);
                    else {
                        assertThat(actual.status()).isEqualTo(GpaGoalStatus.SOLVED);
                        assertThat(actual.assumptions().values().stream()
                                        .max(BigDecimal::compareTo)
                                        .orElseThrow())
                                .isEqualByComparingTo(expectedCap);
                        assertThat(actual.optimal()).isTrue();
                    }
                }
            }
        }
    }

    private GpaGoalSolverPolicy.SearchResult solve(List<Course> courses, String target) {
        return GpaGoalSolverPolicy.solve(courses, decimal(target), 10000, () -> true);
    }

    private BigDecimal decimal(String number) {
        return new BigDecimal(number);
    }
}
