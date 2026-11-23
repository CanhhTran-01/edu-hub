package com.eduhub.edu_hub.enrollment.domain.policy;

import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** Tính kết quả và hạn khóa từ điểm, thang quy đổi, thời hạn do service cung cấp. */
public final class CourseGradePolicy {
    private static final BigDecimal PROCESS_WEIGHT = new BigDecimal("0.3");
    private static final BigDecimal EXAM_WEIGHT = new BigDecimal("0.7");
    private static final BigDecimal PASSING_SCORE = new BigDecimal("4");
    private static final Set<String> SUPPORTED_LETTERS = Set.of("A+", "A", "B+", "B", "C+", "C", "D+", "D", "F");

    private CourseGradePolicy() {}

    public record GradeBand(BigDecimal minimumScore, String letter, BigDecimal gradePoint) {}

    public record GradeResult(BigDecimal score10, String letter, BigDecimal score4, boolean passed) {}

    public static LocalDateTime calculateGradeLockAt(LocalDateTime gradePublishedAt, Duration gradeCorrectionWindow) {
        if (gradeCorrectionWindow == null || gradeCorrectionWindow.isNegative())
            throw new EnrollmentException(ErrorCode.COURSE_GRADE_LOCK_CONFIGURATION_INVALID);
        try {
            return gradePublishedAt.plus(gradeCorrectionWindow);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new EnrollmentException(ErrorCode.COURSE_GRADE_LOCK_CONFIGURATION_INVALID);
        }
    }

    public static GradeResult calculateGrade(
            BigDecimal processScore, BigDecimal finalExamScore, List<GradeBand> gradeScale) {
        var bands = requireValidScale(gradeScale);
        BigDecimal score10 = processScore
                .multiply(PROCESS_WEIGHT)
                .add(finalExamScore.multiply(EXAM_WEIGHT))
                .setScale(1, RoundingMode.HALF_UP);
        // Quy đổi sau khi làm tròn tổng kết; biên dưới thuộc khoảng đang xét.
        GradeBand selectedBand = bands.getFirst();
        for (GradeBand band : bands) {
            if (score10.compareTo(band.minimumScore()) < 0) break;
            selectedBand = band;
        }
        return new GradeResult(
                score10, selectedBand.letter(), selectedBand.gradePoint(), score10.compareTo(PASSING_SCORE) >= 0);
    }

    public static List<GradeBand> requireValidScale(List<GradeBand> gradeScale) {
        if (gradeScale == null || gradeScale.size() < 2) throw invalidScale();
        Set<String> letters = new HashSet<>();
        Set<BigDecimal> thresholds = new HashSet<>();
        for (GradeBand band : gradeScale) {
            if (band == null
                    || band.minimumScore() == null
                    || band.gradePoint() == null
                    || band.letter() == null
                    || !SUPPORTED_LETTERS.contains(band.letter())
                    || !letters.add(band.letter())
                    || band.minimumScore().signum() < 0
                    || band.minimumScore().compareTo(BigDecimal.TEN) > 0
                    || band.minimumScore().stripTrailingZeros().scale() > 1
                    || !thresholds.add(band.minimumScore().stripTrailingZeros())
                    || band.gradePoint().signum() < 0
                    || band.gradePoint().compareTo(new BigDecimal("4")) > 0
                    || band.gradePoint().stripTrailingZeros().scale() > 1) throw invalidScale();
        }
        var bands = gradeScale.stream()
                .sorted(Comparator.comparing(GradeBand::minimumScore))
                .toList();
        if (bands.getFirst().minimumScore().signum() != 0
                || !"F".equals(bands.getFirst().letter())
                || bands.getFirst().gradePoint().signum() != 0
                || bands.get(1).minimumScore().compareTo(PASSING_SCORE) != 0) throw invalidScale();
        for (int bandIndex = 1; bandIndex < bands.size(); bandIndex++) {
            var band = bands.get(bandIndex);
            if ("F".equals(band.letter())
                    || band.gradePoint().signum() <= 0
                    || band.gradePoint().compareTo(bands.get(bandIndex - 1).gradePoint()) < 0) throw invalidScale();
        }
        return bands;
    }

    private static EnrollmentException invalidScale() {
        return new EnrollmentException(ErrorCode.COURSE_GRADE_CONFIGURATION_INVALID);
    }
}
