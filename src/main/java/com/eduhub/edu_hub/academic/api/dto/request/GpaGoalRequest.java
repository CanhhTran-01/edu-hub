package com.eduhub.edu_hub.academic.api.dto.request;

import com.eduhub.edu_hub.academic.domain.enums.CourseGradeComparison;
import java.util.List;

/** Đầu vào facade: AI truyền điều kiện; kỳ, mục tiêu và danh tính vẫn do BE xác định. */
public record GpaGoalRequest(
        String snapshotRef,
        List<CourseGradeConditionRequest> existingConditions,
        List<CourseGradeConditionRequest> conditionChanges) {
    public static final int MAXIMUM_CONDITIONS = 1000;

    /** Các thay đổi cùng một lượt học thay toàn bộ điều kiện cũ của lượt đó, rồi được giao với nhau. */
    public record CourseGradeConditionRequest(
            String courseReference, Long enrollmentId, CourseGradeComparison comparison, String gradeLetter) {}
}
