package com.eduhub.edu_hub.academic.application.service;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest;
import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest.CourseGradeConditionRequest;
import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest;
import com.eduhub.edu_hub.academic.api.dto.response.GpaGoalResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationSnapshotResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationSnapshotResponse.CourseResponse;
import com.eduhub.edu_hub.academic.application.exception.GpaSimulationException;
import com.eduhub.edu_hub.academic.domain.enums.CourseGradeComparison;
import com.eduhub.edu_hub.academic.domain.enums.GpaGoalStatus;
import com.eduhub.edu_hub.academic.domain.policy.GpaGoalSolverPolicy;
import com.eduhub.edu_hub.academic.infrastructure.config.GpaGoalSolverSettings;
import com.eduhub.edu_hub.shared.common.TextsNormalizer;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Chỉ các lần đọc snapshot/preview có transaction; tìm kiếm trên DTO độc lập ở ngoài transaction DB. */
@Service
@RequiredArgsConstructor
public class StudentGpaGoalService {
    private final StudentGpaSimulationService simulationService;
    private final GpaGoalSolverSettings settings;
    private final Clock clock;

    public GpaGoalResponse solveMyGpaGoal(Jwt principal, GpaGoalRequest request) {
        if (principal == null) throw new GpaSimulationException(ErrorCode.UNAUTHENTICATED);
        requireRequest(request);
        var snapshot = simulationService.findMySnapshot(principal);
        if (!snapshot.snapshotRef().equals(request.snapshotRef()))
            throw new GpaSimulationException(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED);
        var response = GpaGoalResponse.builder()
                .snapshot(snapshot)
                .conditions(request.existingConditions())
                .pendingConditions(List.of())
                .calculatedAt(LocalDateTime.now(clock));
        if (snapshot.semesterTargetGpa() == null)
            return response.status(GpaGoalStatus.TARGET_NOT_SET)
                    .pendingConditions(request.conditionChanges())
                    .message("Bạn chưa đặt mục tiêu GPA kỳ. Hãy cập nhật mục tiêu kỳ tại Profile rồi yêu cầu tính lại.")
                    .build();
        if (snapshot.courses().isEmpty())
            return response.status(GpaGoalStatus.NO_COURSES)
                    .message("Kỳ hiện tại chưa có lượt học để tìm phương án GPA.")
                    .build();

        var existing = resolveConditions(snapshot, request.existingConditions());
        if (existing.clarification() != null)
            return clarification(response, request.conditionChanges(), existing.clarification());
        response.conditions(flattenConditions(existing.byEnrollmentId()));
        var changes = resolveConditions(snapshot, request.conditionChanges());
        if (changes.clarification() != null)
            return clarification(response, request.conditionChanges(), changes.clarification());
        var effectiveConditions = new LinkedHashMap<>(existing.byEnrollmentId());
        // Thay điều kiện theo lượt học, không biến điểm solver từng đề xuất thành điều kiện bắt buộc.
        changes.byEnrollmentId().forEach((enrollmentId, conditions) -> {
            if (conditions.size() == 1 && conditions.getFirst().comparison() == CourseGradeComparison.REMOVE)
                effectiveConditions.remove(enrollmentId);
            else effectiveConditions.put(enrollmentId, conditions);
        });

        var pointsByLetter = gradePointsByLetter(snapshot);
        var solverCourses = new ArrayList<GpaGoalSolverPolicy.Course>();
        for (var course : snapshot.courses()) {
            var conditions = effectiveConditions.getOrDefault(course.enrollmentId(), List.of());
            if (conditions.stream()
                            .filter(condition -> condition.comparison() == CourseGradeComparison.EXACT)
                            .map(CourseGradeConditionRequest::gradeLetter)
                            .distinct()
                            .count()
                    > 1)
                return clarification(
                        response,
                        request.conditionChanges(),
                        "Môn " + course.subjectCode()
                                + " đang được yêu cầu giữ hai điểm chữ khác nhau. Bạn muốn giữ mức nào?");
            var grades = snapshot.gradeOptions().stream()
                    .map(GpaSimulationSnapshotResponse.GradeOptionResponse::grade4)
                    .filter(grade ->
                            conditions.stream().allMatch(condition -> allowsGrade(condition, grade, pointsByLetter)))
                    .toList();
            if (grades.isEmpty())
                return clarification(
                        response,
                        request.conditionChanges(),
                        "Các điều kiện của " + course.subjectCode() + " mâu thuẫn nhau. Hãy điều chỉnh lại điều kiện.");
            if (!course.canAdjustGrade()
                    && conditions.stream()
                            .anyMatch(condition -> !allowsPublishedGrade(condition, course, pointsByLetter)))
                return clarification(
                        response,
                        request.conditionChanges(),
                        "Điểm đã công bố của " + course.subjectCode()
                                + " được giữ nguyên và không khớp điều kiện bạn yêu cầu. Bạn muốn bỏ hoặc đổi điều kiện nào?");
            solverCourses.add(new GpaGoalSolverPolicy.Course(
                    course.enrollmentId(), course.credits(), course.actualGrade4(), grades));
        }
        response.conditions(flattenConditions(effectiveConditions));

        long startedNanos = System.nanoTime();
        var search = GpaGoalSolverPolicy.solve(
                solverCourses,
                snapshot.semesterTargetGpa(),
                settings.getMaximumEvaluations(),
                () -> !Thread.currentThread().isInterrupted()
                        && System.nanoTime() - startedNanos
                                < settings.getExecutionTimeout().toNanos());
        response.status(search.status())
                .maximumPossibleSemesterGpa(search.maximumPossibleGpa())
                .optimal(search.optimal())
                .evaluatedAssignments(search.evaluatedAssignments())
                .calculatedAt(LocalDateTime.now(clock));
        if (search.status() == GpaGoalStatus.SEARCH_LIMIT_REACHED)
            return response.message(
                            "Chưa tìm được phương án trong giới hạn xử lý; chưa có căn cứ kết luận không thể đạt.")
                    .build();

        // Tải lại nguồn, đối chiếu snapshot và tính lại bằng cùng policy preview, gồm cả quy tắc học lại/tích lũy.
        var verification = simulationService.previewMyGpa(
                principal,
                new GpaSimulationRequest(
                        snapshot.snapshotRef(),
                        search.assumptions().entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(entry -> new GpaSimulationRequest.CourseAssumptionRequest(
                                        entry.getKey(), entry.getValue()))
                                .toList()));
        if (verification.semesterGpa() == null
                || (verification.semesterGpa().compareTo(snapshot.semesterTargetGpa()) >= 0)
                        != (search.status() == GpaGoalStatus.SOLVED))
            throw new GpaSimulationException(ErrorCode.GPA_SIMULATION_DATA_INVALID);
        if (search.status() == GpaGoalStatus.INFEASIBLE)
            return response.maximumPossibleSemesterGpa(verification.semesterGpa())
                    .message(
                            "Không thể đạt mục tiêu kỳ với điểm đã công bố và các điều kiện hiện tại; ngay cả cận trên cũng thấp hơn mục tiêu.")
                    .build();
        return response.preview(verification)
                .message(
                        search.optimal()
                                ? "Đã tìm được phương án đáp ứng mục tiêu kỳ, ưu tiên tránh thêm môn trượt rồi giảm mức điểm cao nhất phải đạt."
                                : "Đã tìm được phương án đáp ứng mục tiêu kỳ; giới hạn xử lý chưa cho phép chứng minh phương án tối ưu.")
                .build();
    }

    private ConditionResolution resolveConditions(
            GpaSimulationSnapshotResponse snapshot, List<CourseGradeConditionRequest> requestedConditions) {
        var byEnrollmentId = new LinkedHashMap<Long, List<CourseGradeConditionRequest>>();
        var pointsByLetter = gradePointsByLetter(snapshot);
        for (var condition : requestedConditions) {
            String reference = TextsNormalizer.normalizeKey(condition.courseReference());
            var matches = snapshot.courses().stream()
                    .filter(course -> condition.enrollmentId() == null
                            ? reference.equals(TextsNormalizer.normalizeKey(course.subjectCode()))
                                    || reference.equals(TextsNormalizer.normalizeKey(course.subjectName()))
                            : condition.enrollmentId().equals(course.enrollmentId()))
                    .toList();
            if (matches.isEmpty() && condition.enrollmentId() == null)
                matches = snapshot.courses().stream()
                        .filter(course -> TextsNormalizer.normalizeKey(course.subjectName())
                                .contains(reference))
                        .toList();
            if (matches.size() != 1)
                return new ConditionResolution(
                        byEnrollmentId,
                        matches.isEmpty()
                                ? "Chưa đối chiếu được môn '" + condition.courseReference()
                                        + "' với kỳ hiện tại. Bạn muốn áp dụng cho mã học phần nào?"
                                : "Có nhiều lượt học khớp '" + condition.courseReference() + "': "
                                        + matches.stream()
                                                .limit(8)
                                                .map(course ->
                                                        course.subjectCode() + " (lượt " + course.enrollmentId() + ")")
                                                .collect(Collectors.joining(", "))
                                        + ". Bạn muốn chọn lượt học nào?");
            var course = matches.getFirst();
            if (condition.enrollmentId() != null
                    && !reference.isEmpty()
                    && !reference.equals(TextsNormalizer.normalizeKey(course.subjectCode()))
                    && !reference.equals(TextsNormalizer.normalizeKey(course.subjectName())))
                return new ConditionResolution(
                        byEnrollmentId, "Mã lượt học và tên/mã môn không khớp nhau. Bạn muốn chọn môn nào?");
            if (condition.comparison() == CourseGradeComparison.UNSPECIFIED)
                return new ConditionResolution(
                        byEnrollmentId,
                        "Với " + course.subjectCode() + ", bạn muốn đúng " + condition.gradeLetter()
                                + ", tối đa mức đó hay từ mức đó trở lên?");
            String letter = TextsNormalizer.normalizeKey(condition.gradeLetter());
            if (condition.comparison() != CourseGradeComparison.REMOVE && !pointsByLetter.containsKey(letter))
                return new ConditionResolution(
                        byEnrollmentId,
                        "Mức điểm '" + condition.gradeLetter() + "' không có trong thang hiện hành. Hãy chọn: "
                                + String.join(", ", pointsByLetter.keySet()) + ".");
            byEnrollmentId
                    .computeIfAbsent(course.enrollmentId(), ignored -> new ArrayList<>())
                    .add(new CourseGradeConditionRequest(
                            course.subjectCode(),
                            course.enrollmentId(),
                            condition.comparison(),
                            condition.comparison() == CourseGradeComparison.REMOVE ? null : letter));
        }
        if (byEnrollmentId.values().stream()
                .anyMatch(conditions -> conditions.size() > 1
                        && conditions.stream()
                                .anyMatch(condition -> condition.comparison() == CourseGradeComparison.REMOVE)))
            return new ConditionResolution(
                    byEnrollmentId, "Cùng một môn vừa được yêu cầu gỡ vừa thêm điều kiện. Bạn muốn giữ yêu cầu nào?");
        return new ConditionResolution(byEnrollmentId, null);
    }

    private Map<String, BigDecimal> gradePointsByLetter(GpaSimulationSnapshotResponse snapshot) {
        var pointsByLetter = new LinkedHashMap<String, BigDecimal>();
        snapshot.gradeOptions().forEach(option -> option.letters()
                .forEach(letter -> pointsByLetter.put(TextsNormalizer.normalizeKey(letter), option.grade4())));
        return pointsByLetter;
    }

    private boolean allowsGrade(
            CourseGradeConditionRequest condition, BigDecimal grade, Map<String, BigDecimal> pointsByLetter) {
        if (condition.comparison() == CourseGradeComparison.REMOVE) return true;
        int comparison = grade.compareTo(pointsByLetter.get(condition.gradeLetter()));
        return switch (condition.comparison()) {
            case EXACT -> comparison == 0;
            case AT_MOST -> comparison <= 0;
            case AT_LEAST -> comparison >= 0;
            default -> false;
        };
    }

    private boolean allowsPublishedGrade(
            CourseGradeConditionRequest condition, CourseResponse course, Map<String, BigDecimal> pointsByLetter) {
        if (!allowsGrade(condition, course.actualGrade4(), pointsByLetter)) return false;
        // Hai nhãn có thể cùng hệ 4; điều kiện đúng điểm chữ không được sửa nhãn đã công bố.
        return condition.comparison() != CourseGradeComparison.EXACT
                || course.actualGradeLetter() == null
                || condition.gradeLetter().equals(TextsNormalizer.normalizeKey(course.actualGradeLetter()));
    }

    private List<CourseGradeConditionRequest> flattenConditions(
            Map<Long, List<CourseGradeConditionRequest>> conditions) {
        return conditions.values().stream().flatMap(Collection::stream).toList();
    }

    private GpaGoalResponse clarification(
            GpaGoalResponse.GpaGoalResponseBuilder response,
            List<CourseGradeConditionRequest> pendingConditions,
            String message) {
        return response.status(GpaGoalStatus.NEEDS_CLARIFICATION)
                .pendingConditions(pendingConditions)
                .message(message)
                .build();
    }

    private void requireRequest(GpaGoalRequest request) {
        if (request == null
                || request.snapshotRef() == null
                || request.existingConditions() == null
                || request.conditionChanges() == null
                || request.existingConditions().size()
                                + request.conditionChanges().size()
                        > GpaGoalRequest.MAXIMUM_CONDITIONS)
            throw new GpaSimulationException(
                    ErrorCode.VALIDATION_ERROR, "conditions", "Danh sách điều kiện GPA không hợp lệ.");
        var allConditions = new ArrayList<>(request.existingConditions());
        allConditions.addAll(request.conditionChanges());
        for (var condition : allConditions) {
            if (condition == null
                    || condition.comparison() == null
                    || (condition.enrollmentId() != null && condition.enrollmentId() <= 0)
                    || (condition.enrollmentId() == null
                            && TextsNormalizer.normalizeKey(condition.courseReference())
                                    .isEmpty())
                    || (condition.courseReference() != null
                            && condition.courseReference().length() > 255)
                    || (condition.gradeLetter() != null
                            && condition.gradeLetter().length() > 10)
                    || (condition.comparison() != CourseGradeComparison.REMOVE
                            && TextsNormalizer.normalizeKey(condition.gradeLetter())
                                    .isEmpty()))
                throw new GpaSimulationException(
                        ErrorCode.VALIDATION_ERROR,
                        "conditions",
                        "Mỗi điều kiện phải có môn, cách so sánh và mức điểm phù hợp.");
        }
    }

    private record ConditionResolution(
            Map<Long, List<CourseGradeConditionRequest>> byEnrollmentId, String clarification) {}
}
