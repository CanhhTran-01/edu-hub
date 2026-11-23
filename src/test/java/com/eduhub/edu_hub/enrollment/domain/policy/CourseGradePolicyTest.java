package com.eduhub.edu_hub.enrollment.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.policy.CourseGradePolicy.GradeBand;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourseGradePolicyTest {
    // Thang đầu vào của unit test, không được dùng làm mặc định quy chế trong ứng dụng.
    private final List<GradeBand> gradeScale = List.of(
            band("0", "F", "0"),
            band("4", "D", "1"),
            band("5.5", "C", "2"),
            band("7", "B", "3"),
            band("8", "B+", "3.5"),
            band("9", "A+", "4"));

    @Test
    void roundsTheWeightedScoreBeforeSelectingTheInclusiveGradeBoundary() {
        var grade = CourseGradePolicy.calculateGrade(new BigDecimal("7.95"), new BigDecimal("7.95"), gradeScale);
        assertThat(grade.score10()).isEqualByComparingTo("8.0");
        assertThat(grade.letter()).isEqualTo("B+");
        assertThat(grade.score4()).isEqualByComparingTo("3.5");
        assertThat(grade.passed()).isTrue();
    }

    @Test
    void distinguishesZeroFromMissingGradesAndKeepsPassingBoundaryAtFour() {
        var failed = CourseGradePolicy.calculateGrade(BigDecimal.ZERO, BigDecimal.ZERO, gradeScale);
        assertThat(failed.score10()).isZero();
        assertThat(failed.score4()).isZero();
        assertThat(failed.letter()).isEqualTo("F");
        assertThat(failed.passed()).isFalse();
        var passed = CourseGradePolicy.calculateGrade(new BigDecimal("4"), new BigDecimal("4"), gradeScale);
        assertThat(passed.passed()).isTrue();
        assertThat(passed.letter()).isEqualTo("D");
    }

    @Test
    void rejectsMissingScaleInsteadOfInventingOfficialConversionRules() {
        assertThatThrownBy(() -> CourseGradePolicy.calculateGrade(BigDecimal.TEN, BigDecimal.TEN, List.of()))
                .isInstanceOfSatisfying(EnrollmentException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.COURSE_GRADE_CONFIGURATION_INVALID));
    }

    @Test
    void rejectsDuplicateThresholdsAndAConversionInconsistentWithDatabasePassingRules() {
        assertThatThrownBy(() -> CourseGradePolicy.requireValidScale(
                        List.of(band("0", "F", "0"), band("4", "D", "1"), band("4.0", "C", "2"))))
                .isInstanceOf(EnrollmentException.class);
        assertThatThrownBy(() -> CourseGradePolicy.requireValidScale(List.of(band("0", "F", "0"), band("5", "D", "1"))))
                .isInstanceOf(EnrollmentException.class);
    }

    @Test
    void calculatesTheDeadlineFromTheProvidedWindowIncludingImmediateLock() {
        var publishedAt = LocalDateTime.parse("2026-11-23T10:00:00");
        assertThat(CourseGradePolicy.calculateGradeLockAt(publishedAt, Duration.ofHours(36)))
                .isEqualTo(publishedAt.plusHours(36));
        assertThat(CourseGradePolicy.calculateGradeLockAt(publishedAt, Duration.ZERO))
                .isEqualTo(publishedAt);
    }

    @Test
    void rejectsMissingNegativeOrOverflowingWindowsAsConfigurationErrors() {
        var publishedAt = LocalDateTime.parse("2026-11-23T10:00:00");
        for (Duration window :
                java.util.Arrays.asList(null, Duration.ofSeconds(-1), Duration.ofSeconds(Long.MAX_VALUE))) {
            assertThatThrownBy(() -> CourseGradePolicy.calculateGradeLockAt(publishedAt, window))
                    .isInstanceOfSatisfying(EnrollmentException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.COURSE_GRADE_LOCK_CONFIGURATION_INVALID));
        }
    }

    private GradeBand band(String minimumScore, String letter, String gradePoint) {
        return new GradeBand(new BigDecimal(minimumScore), letter, new BigDecimal(gradePoint));
    }
}
