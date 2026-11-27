package com.eduhub.edu_hub.enrollment.domain.policy;

import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.policy.CourseGradePolicy.GradeBand;
import com.eduhub.edu_hub.enrollment.domain.policy.CourseGradePolicy.GradeResult;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Tính ngược bằng chính phép tính/làm tròn/quy đổi hiện hành, không chia ngược một ngưỡng rồi làm tròn xuống. */
public final class MinimumExamScorePolicy {
    public static final int EXAM_SCORE_DECIMAL_PLACES = 3;
    private static final int MAXIMUM_EXAM_MILLIPOINTS = 10000;

    private MinimumExamScorePolicy() {}

    public record Calculation(
            BigDecimal processScore,
            BigDecimal minimumExamScore,
            BigDecimal minimumExamScoreTwoDecimals,
            GradeResult achievedGrade,
            GradeResult maximumGrade) {
        public boolean reachable() {
            return minimumExamScore != null;
        }
    }

    public static Calculation solve(
            BigDecimal componentScore1,
            BigDecimal componentScore2,
            String targetGradeLetter,
            List<GradeBand> suppliedScale) {
        if (componentScore1 == null
                || componentScore2 == null
                || !ProcessScorePolicy.isValidComponentScore(componentScore1)
                || !ProcessScorePolicy.isValidComponentScore(componentScore2))
            throw new EnrollmentException(ErrorCode.VALIDATION_ERROR);
        var scale = CourseGradePolicy.requireValidScale(suppliedScale);
        var target = scale.stream()
                .filter(band -> band.letter().equals(targetGradeLetter))
                .findFirst()
                .orElseThrow(() -> new EnrollmentException(ErrorCode.VALIDATION_ERROR));
        BigDecimal processScore = ProcessScorePolicy.calculateProcessScore(componentScore1, componentScore2);
        var maximumGrade = CourseGradePolicy.calculateGrade(processScore, BigDecimal.TEN, scale);
        if (maximumGrade.score10().compareTo(target.minimumScore()) < 0)
            return new Calculation(processScore, null, null, null, maximumGrade);

        // Tổng kết tăng đơn điệu theo điểm thi. Mỗi bước vẫn đi qua đủ hai tầng làm tròn của Enrollment.
        int lowerMillipoints = 0;
        int upperMillipoints = MAXIMUM_EXAM_MILLIPOINTS;
        while (lowerMillipoints < upperMillipoints) {
            int middleMillipoints = (lowerMillipoints + upperMillipoints) / 2;
            var grade = CourseGradePolicy.calculateGrade(processScore, examScore(middleMillipoints), scale);
            if (grade.score10().compareTo(target.minimumScore()) >= 0) upperMillipoints = middleMillipoints;
            else lowerMillipoints = middleMillipoints + 1;
        }
        BigDecimal minimumExamScore = examScore(lowerMillipoints);
        var achievedGrade = CourseGradePolicy.calculateGrade(processScore, minimumExamScore, scale);
        BigDecimal twoDecimalScore = minimumExamScore.setScale(2, RoundingMode.CEILING);
        var twoDecimalGrade = CourseGradePolicy.calculateGrade(processScore, twoDecimalScore, scale);
        // A và A+ có thể cùng hệ 4 nhưng khác ngưỡng hệ 10, nên không so bằng điểm hệ 4.
        if (achievedGrade.score10().compareTo(target.minimumScore()) < 0
                || twoDecimalGrade.score10().compareTo(target.minimumScore()) < 0
                || (lowerMillipoints > 0
                        && CourseGradePolicy.calculateGrade(processScore, examScore(lowerMillipoints - 1), scale)
                                        .score10()
                                        .compareTo(target.minimumScore())
                                >= 0)) throw new EnrollmentException(ErrorCode.UNEXPECTED_ERROR);
        return new Calculation(processScore, minimumExamScore, twoDecimalScore, achievedGrade, maximumGrade);
    }

    private static BigDecimal examScore(int millipoints) {
        return BigDecimal.valueOf(millipoints, EXAM_SCORE_DECIMAL_PLACES);
    }
}
