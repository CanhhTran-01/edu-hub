package com.eduhub.edu_hub.academic.domain.policy;

import com.eduhub.edu_hub.academic.domain.enums.GpaGoalStatus;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Tìm trên miền điểm hữu hạn; chỉ dùng đầu vào và tín hiệu ngân sách do service cung cấp. */
public final class GpaGoalSolverPolicy {
    private GpaGoalSolverPolicy() {}

    public record Course(Long enrollmentId, int credits, BigDecimal publishedGrade, List<BigDecimal> allowedGrades) {}

    public record SearchResult(
            GpaGoalStatus status,
            Map<Long, BigDecimal> assumptions,
            BigDecimal maximumPossibleGpa,
            boolean optimal,
            int evaluatedAssignments) {}

    public static SearchResult solve(
            List<Course> courses, BigDecimal targetGpa, int maximumEvaluations, BooleanSupplier hasRemainingTime) {
        var budget = new EvaluationBudget(maximumEvaluations, hasRemainingTime);
        var domainsByEnrollmentId = new LinkedHashMap<Long, List<BigDecimal>>();
        var upperBoundAssumptions = new LinkedHashMap<Long, BigDecimal>();
        for (var course : courses) {
            if (course.publishedGrade() != null) continue;
            var domain = course.allowedGrades().stream().sorted().distinct().toList();
            if (domain.isEmpty()) throw new IllegalArgumentException("Miền điểm của lượt học không được rỗng.");
            // Điểm dương lớn nhất không thấp hơn F: bỏ F nếu còn điểm đạt không làm mất tính khả thi.
            // F đã công bố hoặc bị điều kiện bắt buộc giữ vẫn được bảo toàn.
            var passingGrades =
                    domain.stream().filter(grade -> grade.signum() > 0).toList();
            if (!passingGrades.isEmpty()) domain = passingGrades;
            domainsByEnrollmentId.put(course.enrollmentId(), domain);
            upperBoundAssumptions.put(course.enrollmentId(), domain.getLast());
        }

        // Gán từng môn mức cao nhất hợp lệ tạo cận trên chính xác vì mục tiêu chỉ là GPA kỳ tối thiểu.
        if (!budget.tryEvaluate()) return searchLimit(budget);
        BigDecimal maximumGpa = calculateGpa(courses, upperBoundAssumptions);
        if (maximumGpa == null) throw new IllegalArgumentException("Không có tín chỉ để tính GPA kỳ.");
        if (maximumGpa.compareTo(targetGpa) < 0)
            return new SearchResult(
                    GpaGoalStatus.INFEASIBLE, Map.copyOf(upperBoundAssumptions), maximumGpa, true, budget.evaluated);

        var bestAssumptions = new LinkedHashMap<>(upperBoundAssumptions);
        boolean optimal = domainsByEnrollmentId.isEmpty();
        var gradeCaps = new TreeSet<BigDecimal>();
        domainsByEnrollmentId.values().forEach(gradeCaps::addAll);
        for (var cap : gradeCaps) {
            var candidate = new LinkedHashMap<Long, BigDecimal>();
            boolean coversEveryCourse = true;
            for (var entry : domainsByEnrollmentId.entrySet()) {
                var cappedGrades = entry.getValue().stream()
                        .filter(grade -> grade.compareTo(cap) <= 0)
                        .toList();
                if (cappedGrades.isEmpty()) {
                    coversEveryCourse = false;
                    break;
                }
                candidate.put(entry.getKey(), cappedGrades.getLast());
            }
            if (!coversEveryCourse) continue;
            if (!budget.tryEvaluate()) break;
            if (calculateGpa(courses, candidate).compareTo(targetGpa) >= 0) {
                bestAssumptions = candidate;
                optimal = true;
                break;
            }
        }

        // Đồng hạng được giảm điểm theo thứ tự môn do service chốt, không suy môn dễ hoặc khó.
        // Chỉ tiêu tối ưu được công bố là tránh F có thể tránh và giảm điểm cao nhất, không tối ưu tổng điểm dư.
        for (var entry : domainsByEnrollmentId.entrySet()) {
            BigDecimal previousGrade = bestAssumptions.get(entry.getKey());
            for (var grade : entry.getValue()) {
                if (grade.compareTo(previousGrade) >= 0) break;
                if (!budget.tryEvaluate())
                    return new SearchResult(
                            GpaGoalStatus.SOLVED, Map.copyOf(bestAssumptions), maximumGpa, optimal, budget.evaluated);
                var candidate = new LinkedHashMap<>(bestAssumptions);
                candidate.put(entry.getKey(), grade);
                if (calculateGpa(courses, candidate).compareTo(targetGpa) >= 0) {
                    bestAssumptions = candidate;
                    break;
                }
            }
        }
        return new SearchResult(
                GpaGoalStatus.SOLVED, Map.copyOf(bestAssumptions), maximumGpa, optimal, budget.evaluated);
    }

    private static BigDecimal calculateGpa(List<Course> courses, Map<Long, BigDecimal> assumptions) {
        BigDecimal weightedScore = BigDecimal.ZERO;
        long gradedCredits = 0;
        for (var course : courses) {
            if (course.credits() <= 0) throw new IllegalArgumentException("Tín chỉ lượt học phải dương.");
            BigDecimal grade =
                    course.publishedGrade() == null ? assumptions.get(course.enrollmentId()) : course.publishedGrade();
            weightedScore = weightedScore.add(grade.multiply(BigDecimal.valueOf(course.credits())));
            gradedCredits += course.credits();
        }
        return AcademicPerformancePolicy.calculateGpaFromWeightedScore(weightedScore, gradedCredits);
    }

    private static SearchResult searchLimit(EvaluationBudget budget) {
        return new SearchResult(GpaGoalStatus.SEARCH_LIMIT_REACHED, Map.of(), null, false, budget.evaluated);
    }

    private static final class EvaluationBudget {
        private final int maximumEvaluations;
        private final BooleanSupplier hasRemainingTime;
        private int evaluated;

        private EvaluationBudget(int maximumEvaluations, BooleanSupplier hasRemainingTime) {
            this.maximumEvaluations = maximumEvaluations;
            this.hasRemainingTime = hasRemainingTime;
        }

        private boolean tryEvaluate() {
            if (evaluated >= maximumEvaluations || !hasRemainingTime.getAsBoolean()) return false;
            evaluated++;
            return true;
        }
    }
}
