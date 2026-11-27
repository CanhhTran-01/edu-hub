package com.eduhub.edu_hub.academic.domain.policy;

import com.eduhub.edu_hub.academic.application.exception.GpaSimulationException;
import com.eduhub.edu_hub.academic.domain.policy.AcademicPerformancePolicy.GradedAttempt;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Tính trên các lượt độc lập JPA; điểm thật đã có không được thay bằng giả định. */
public final class GpaSimulationPolicy {
    private GpaSimulationPolicy() {}

    public record Attempt(
            Long enrollmentId,
            Long subjectId,
            Long semesterId,
            String subjectCode,
            String subjectName,
            int credits,
            int academicYearStart,
            int semesterNumber,
            LocalDateTime registeredAt,
            BigDecimal actualGrade4,
            String actualGradeLetter,
            boolean passed) {
        public boolean hasPublishedGrade() {
            return actualGrade4 != null;
        }
    }

    public record Calculation(
            BigDecimal semesterGpa, BigDecimal cumulativeGpa, Integer accumulatedCredits, int semesterCredits) {}

    public static Calculation simulate(
            List<Attempt> attemptsThroughSemester,
            Long semesterId,
            Map<Long, BigDecimal> assumptions,
            Collection<BigDecimal> allowedGrades,
            boolean historyComplete) {
        var semesterAttempts = attemptsThroughSemester.stream()
                .filter(attempt -> semesterId.equals(attempt.semesterId()))
                .toList();
        Set<Long> adjustableEnrollmentIds = new HashSet<>();
        for (var attempt : semesterAttempts) {
            if (!attempt.hasPublishedGrade()) adjustableEnrollmentIds.add(attempt.enrollmentId());
        }

        // Đủ đúng một giả định cho mỗi lượt chưa có kết quả; không chấp nhận ID ngoài kỳ hoặc điểm thật.
        if (!assumptions.keySet().equals(adjustableEnrollmentIds))
            throw invalid("assumptions", "Phải gửi đúng các lượt chưa có kết quả của kỳ được chọn.");
        for (var assumption : assumptions.entrySet()) {
            if (assumption.getValue() == null
                    || allowedGrades.stream().noneMatch(grade -> grade.compareTo(assumption.getValue()) == 0))
                throw invalid("assumptions", "Điểm giả định phải thuộc thang điểm đang áp dụng.");
        }

        var projectedGrades = new ArrayList<GradedAttempt>();
        var projectedSemesterGrades = new ArrayList<GradedAttempt>();
        int semesterCredits = 0;
        for (var attempt : attemptsThroughSemester) {
            if (attempt.credits() <= 0 || attempt.registeredAt() == null)
                throw new GpaSimulationException(ErrorCode.GPA_SIMULATION_DATA_INVALID);
            boolean belongsToSemester = semesterId.equals(attempt.semesterId());
            if (belongsToSemester) semesterCredits = Math.addExact(semesterCredits, attempt.credits());
            BigDecimal grade4 = belongsToSemester && !attempt.hasPublishedGrade()
                    ? assumptions.get(attempt.enrollmentId())
                    : attempt.actualGrade4();
            if (grade4 == null) continue;
            var gradedAttempt = new GradedAttempt(
                    attempt.enrollmentId(),
                    attempt.subjectId(),
                    attempt.credits(),
                    attempt.academicYearStart(),
                    attempt.semesterNumber(),
                    attempt.registeredAt(),
                    grade4,
                    attempt.hasPublishedGrade() ? attempt.passed() : grade4.signum() > 0);
            projectedGrades.add(gradedAttempt);
            if (belongsToSemester) projectedSemesterGrades.add(gradedAttempt);
        }

        // Chọn lại lượt có kết quả mới nhất trước khi cộng tích lũy; học lại thay lượt cũ, không cộng trùng.
        var latestGrades = AcademicPerformancePolicy.selectLatestAttemptsBySubject(projectedGrades);
        return new Calculation(
                AcademicPerformancePolicy.calculateGpa(projectedSemesterGrades),
                historyComplete ? AcademicPerformancePolicy.calculateGpa(latestGrades) : null,
                historyComplete ? AcademicPerformancePolicy.calculateAccumulatedCredits(latestGrades) : null,
                semesterCredits);
    }

    private static GpaSimulationException invalid(String field, String message) {
        return new GpaSimulationException(ErrorCode.VALIDATION_ERROR, field, message);
    }
}
