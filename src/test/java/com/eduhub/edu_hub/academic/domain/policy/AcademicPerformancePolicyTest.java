package com.eduhub.edu_hub.academic.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.academic.domain.policy.AcademicPerformancePolicy.GradedAttempt;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcademicPerformancePolicyTest {
    @Test
    void weightsCreditsIncludesFailureAndRoundsOnlyFinalGpa() {
        var attempts = List.of(
                attempt(1, 10, 3, 2025, 1, "4", true),
                attempt(2, 20, 2, 2025, 1, "0", false),
                attempt(3, 30, 1, 2025, 1, "3.5", true));
        assertThat(AcademicPerformancePolicy.calculateGpa(attempts)).isEqualByComparingTo("2.58");
        assertThat(AcademicPerformancePolicy.calculateAccumulatedCredits(attempts))
                .isEqualTo(4);
        assertThat(AcademicPerformancePolicy.calculateGpa(List.of())).isNull();
        assertThat(AcademicPerformancePolicy.calculateGpa(List.of(attempts.get(1))))
                .isEqualByComparingTo("0");
    }

    @Test
    void selectsLatestGradedAttemptRatherThanHighestScoreOrLargestId() {
        var earlier = attempt(999, 10, 2, 2025, 1, "4", true);
        var later = attempt(1, 10, 3, 2026, 1, "0", false);
        var pending = attempt(1000, 10, 5, 2026, 2, null, false);
        var selected = AcademicPerformancePolicy.selectLatestAttemptsBySubject(List.of(earlier, later, pending));
        assertThat(selected).containsExactly(later);
        assertThat(AcademicPerformancePolicy.calculateAccumulatedCredits(selected))
                .isZero();
        assertThat(AcademicPerformancePolicy.calculateGpa(selected)).isEqualByComparingTo("0");
    }

    @Test
    void breaksSameSemesterTiesByRegistrationTimeThenEnrollmentIdAndUsesSelectedCredits() {
        var first = attempt(10, 10, 2, 2026, 1, "1", true);
        var second = attempt(11, 10, 3, 2026, 1, "3", true);
        var selected = AcademicPerformancePolicy.selectLatestAttemptsBySubject(List.of(second, first));
        assertThat(selected).containsExactly(second);
        assertThat(AcademicPerformancePolicy.calculateAccumulatedCredits(selected))
                .isEqualTo(3);
    }

    private GradedAttempt attempt(
            long enrollmentId, long subjectId, int credits, int year, int semester, String score, boolean passed) {
        return new GradedAttempt(
                enrollmentId,
                subjectId,
                credits,
                year,
                semester,
                LocalDateTime.of(year, 1, 1, 0, 0),
                score == null ? null : new BigDecimal(score),
                passed);
    }
}
