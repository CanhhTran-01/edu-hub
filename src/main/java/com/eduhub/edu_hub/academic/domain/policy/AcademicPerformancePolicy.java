package com.eduhub.edu_hub.academic.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/** Tổng hợp kết quả đã lưu; không tính điểm học phần hoặc quyết định đạt/rớt. */
public final class AcademicPerformancePolicy {
    private AcademicPerformancePolicy() {}

    public record GradedAttempt(
            Long enrollmentId,
            Long subjectId,
            int credits,
            int academicYearStart,
            int semesterNumber,
            LocalDateTime registeredAt,
            BigDecimal courseScore4,
            boolean passed) {}

    private static final Comparator<GradedAttempt> ATTEMPT_ORDER = Comparator.comparingInt(
                    GradedAttempt::academicYearStart)
            .thenComparingInt(GradedAttempt::semesterNumber)
            .thenComparing(GradedAttempt::registeredAt)
            .thenComparing(GradedAttempt::enrollmentId);

    public static List<GradedAttempt> selectLatestAttemptsBySubject(Collection<GradedAttempt> attempts) {
        var latestAttemptsBySubject = new TreeMap<Long, GradedAttempt>();
        for (var attempt : attempts) {
            if (attempt.courseScore4() == null) continue;
            latestAttemptsBySubject.merge(
                    attempt.subjectId(),
                    attempt,
                    (previous, next) -> ATTEMPT_ORDER.compare(previous, next) < 0 ? next : previous);
        }
        return List.copyOf(latestAttemptsBySubject.values());
    }

    public static BigDecimal calculateGpa(Collection<GradedAttempt> attempts) {
        BigDecimal weightedScore = BigDecimal.ZERO;
        long gradedCredits = 0;
        for (var attempt : attempts) {
            if (attempt.courseScore4() == null) continue;
            weightedScore = weightedScore.add(attempt.courseScore4().multiply(BigDecimal.valueOf(attempt.credits())));
            gradedCredits += attempt.credits();
        }
        // Điểm F = 0 vẫn có trọng số; không có mẫu số thì chưa có GPA, khác với GPA bằng 0.
        return gradedCredits == 0
                ? null
                : weightedScore.divide(BigDecimal.valueOf(gradedCredits), 2, RoundingMode.HALF_UP);
    }

    public static int calculateAccumulatedCredits(Collection<GradedAttempt> latestAttempts) {
        return latestAttempts.stream()
                .filter(GradedAttempt::passed)
                .mapToInt(GradedAttempt::credits)
                .sum();
    }
}
