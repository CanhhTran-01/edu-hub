package com.eduhub.edu_hub.enrollment.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.policy.CourseGradePolicy.GradeBand;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class MinimumExamScorePolicyTest {
    // Thang thử nghiệm chỉ phục vụ kiểm tra biên; ứng dụng luôn đọc quy chế từ cấu hình Enrollment.
    final List<GradeBand> scale = List.of(
            band("0", "F", "0"),
            band("4", "D", "1"),
            band("5.5", "C", "2"),
            band("7", "B", "3"),
            band("7.8", "B+", "3.5"),
            band("8.5", "A", "4"),
            band("9", "A+", "4"));

    @Test
    void solvesTheQuickToolExampleAndRejectsTheImmediatelyLowerMillipoint() {
        var calculation = MinimumExamScorePolicy.solve(decimal("8"), decimal("7"), "A", scale);
        assertThat(calculation.processScore()).isEqualByComparingTo("7.3");
        assertThat(calculation.minimumExamScore()).isEqualByComparingTo("8.943");
        assertThat(calculation.minimumExamScoreTwoDecimals()).isEqualByComparingTo("8.95");
        assertThat(calculation.achievedGrade().letter()).isEqualTo("A");
        assertThat(CourseGradePolicy.calculateGrade(calculation.processScore(), decimal("8.942"), scale)
                        .letter())
                .isEqualTo("B+");
    }

    @Test
    void equalGradePointsDoNotMakeAAndAPlusTheSameExamTarget() {
        var a = MinimumExamScorePolicy.solve(decimal("8"), decimal("7"), "A", scale);
        var aPlus = MinimumExamScorePolicy.solve(decimal("8"), decimal("7"), "A+", scale);
        assertThat(aPlus.minimumExamScore()).isGreaterThan(a.minimumExamScore()).isEqualByComparingTo("9.658");
        assertThat(aPlus.achievedGrade().letter()).isEqualTo("A+");
    }

    @Test
    void zeroIsARealInputAndMaximumExamGradeProvesAnUnreachableTarget() {
        var calculation = MinimumExamScorePolicy.solve(BigDecimal.ZERO, BigDecimal.ZERO, "A", scale);
        assertThat(calculation.reachable()).isFalse();
        assertThat(calculation.processScore()).isZero();
        assertThat(calculation.maximumGrade().score10()).isEqualByComparingTo("7.0");
        assertThat(calculation.minimumExamScore()).isNull();
        var failedTarget = MinimumExamScorePolicy.solve(BigDecimal.ZERO, BigDecimal.ZERO, "F", scale);
        assertThat(failedTarget.minimumExamScore()).isZero();
    }

    @Test
    void rejectsInvalidComponentPrecisionInsteadOfRoundingTheInputToMakeItValid() {
        assertThatThrownBy(() -> MinimumExamScorePolicy.solve(decimal("8.0001"), decimal("7"), "A", scale))
                .isInstanceOf(EnrollmentException.class);
        assertThatThrownBy(() -> MinimumExamScorePolicy.solve(decimal("-1"), decimal("7"), "A", scale))
                .isInstanceOf(EnrollmentException.class);
    }

    @Test
    void binarySearchMatchesIndependentExhaustiveMillipointEnumeration() {
        for (var components : List.of(
                new String[] {"0", "0"}, new String[] {"8", "7"}, new String[] {"9.997", "5.555"}, new String[] {
                    "10", "10"
                })) {
            var first = decimal(components[0]);
            var second = decimal(components[1]);
            var processScore = first.multiply(decimal("0.3"))
                    .add(second.multiply(decimal("0.7")))
                    .setScale(1, RoundingMode.HALF_UP);
            for (var target : scale) {
                BigDecimal expected = null;
                for (int millipoints = 0; millipoints <= 10000; millipoints++) {
                    var exam = BigDecimal.valueOf(millipoints, 3);
                    var total = processScore
                            .multiply(decimal("0.3"))
                            .add(exam.multiply(decimal("0.7")))
                            .setScale(1, RoundingMode.HALF_UP);
                    if (total.compareTo(target.minimumScore()) >= 0) {
                        expected = exam;
                        break;
                    }
                }
                var actual = MinimumExamScorePolicy.solve(first, second, target.letter(), scale);
                if (expected == null) assertThat(actual.reachable()).isFalse();
                else assertThat(actual.minimumExamScore()).isEqualByComparingTo(expected);
            }
        }
    }

    private GradeBand band(String score, String letter, String points) {
        return new GradeBand(decimal(score), letter, decimal(points));
    }

    private BigDecimal decimal(String number) {
        return new BigDecimal(number);
    }
}
