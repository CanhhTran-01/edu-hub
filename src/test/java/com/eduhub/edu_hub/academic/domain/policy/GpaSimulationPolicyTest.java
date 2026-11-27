package com.eduhub.edu_hub.academic.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.academic.application.exception.GpaSimulationException;
import com.eduhub.edu_hub.academic.domain.policy.GpaSimulationPolicy.Attempt;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class GpaSimulationPolicyTest {
    private static final List<BigDecimal> GRADES = List.of("0", "1", "1.5", "2", "2.5", "3", "3.5", "4").stream()
            .map(BigDecimal::new)
            .toList();

    @Test
    void retakeReplacesOldCreditsAndAnAssumedFailureStillParticipatesInGpa() {
        var attempts = List.of(attempt(1, 10, 1, 2, "0"), attempt(2, 10, 2, 3, null), attempt(3, 20, 2, 4, null));

        var calculation = GpaSimulationPolicy.simulate(
                attempts, 2L, Map.of(2L, new BigDecimal("3"), 3L, BigDecimal.ZERO), GRADES, true);

        assertThat(calculation.semesterGpa()).isEqualByComparingTo("1.29");
        assertThat(calculation.cumulativeGpa()).isEqualByComparingTo("1.29");
        assertThat(calculation.accumulatedCredits()).isEqualTo(3);
        assertThat(calculation.semesterCredits()).isEqualTo(7);
        assertThat(attempts.get(1).actualGrade4()).isNull();
    }

    @Test
    void latestAttemptWinsEvenWhenItsGradeIsLowerThanThePreviousAttempt() {
        var calculation = GpaSimulationPolicy.simulate(
                List.of(attempt(1, 10, 1, 2, "4"), attempt(2, 10, 2, 3, null)),
                2L,
                Map.of(2L, BigDecimal.ZERO),
                GRADES,
                true);
        assertThat(calculation.cumulativeGpa()).isEqualByComparingTo("0.00");
        assertThat(calculation.accumulatedCredits()).isZero();
    }

    @Test
    void publishedGradesAreFixedAndCannotAppearInTheAssumptions() {
        var published = attempt(1, 10, 2, 3, "2.5");
        assertThatThrownBy(() -> GpaSimulationPolicy.simulate(
                        List.of(published), 2L, Map.of(1L, new BigDecimal("4")), GRADES, true))
                .isInstanceOf(GpaSimulationException.class);
        assertThat(GpaSimulationPolicy.simulate(List.of(published), 2L, Map.of(), GRADES, true)
                        .semesterGpa())
                .isEqualByComparingTo("2.50");
    }

    @Test
    void missingForeignAndOutOfScaleAssumptionsAreRejected() {
        var attempts = List.of(attempt(1, 10, 2, 3, null));
        for (var assumptions :
                List.of(Map.<Long, BigDecimal>of(), Map.of(99L, BigDecimal.ONE), Map.of(1L, new BigDecimal("0.5")))) {
            assertThatThrownBy(() -> GpaSimulationPolicy.simulate(attempts, 2L, assumptions, GRADES, true))
                    .isInstanceOfSatisfying(
                            GpaSimulationException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
    }

    @Test
    void incompleteHistoryKeepsSemesterPreviewButDoesNotClaimCumulativeResults() {
        var calculation = GpaSimulationPolicy.simulate(
                List.of(attempt(1, 10, 2, 3, null)), 2L, Map.of(1L, new BigDecimal("4.0")), GRADES, false);
        assertThat(calculation.semesterGpa()).isEqualByComparingTo("4.00");
        assertThat(calculation.cumulativeGpa()).isNull();
        assertThat(calculation.accumulatedCredits()).isNull();
    }

    @Test
    void emptySemesterHasNoGpaAndDoesNotTurnMissingResultsIntoZero() {
        var calculation = GpaSimulationPolicy.simulate(List.of(), 2L, Map.of(), GRADES, true);
        assertThat(calculation.semesterGpa()).isNull();
        assertThat(calculation.cumulativeGpa()).isNull();
        assertThat(calculation.accumulatedCredits()).isZero();
    }

    private Attempt attempt(long enrollmentId, long subjectId, long semesterId, int credits, String grade) {
        return new Attempt(
                enrollmentId,
                subjectId,
                semesterId,
                "CS" + subjectId,
                "Học phần " + subjectId,
                credits,
                2024 + (int) semesterId,
                1,
                LocalDateTime.of(2024 + (int) semesterId, 8, 1, 0, 0),
                grade == null ? null : new BigDecimal(grade),
                grade == null ? null : "B",
                grade != null && !grade.equals("0"));
    }
}
