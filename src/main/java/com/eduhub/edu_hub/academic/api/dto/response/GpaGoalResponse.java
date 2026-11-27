package com.eduhub.edu_hub.academic.api.dto.response;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest.CourseGradeConditionRequest;
import com.eduhub.edu_hub.academic.domain.enums.GpaGoalStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/** Snapshot giữ đủ môn, tín chỉ và thang đã dùng để đọc lại kết quả khi dữ liệu hiện hành thay đổi. */
@Builder(toBuilder = true)
public record GpaGoalResponse(
        GpaGoalStatus status,
        GpaSimulationSnapshotResponse snapshot,
        List<CourseGradeConditionRequest> conditions,
        List<CourseGradeConditionRequest> pendingConditions,
        GpaSimulationPreviewResponse preview,
        BigDecimal maximumPossibleSemesterGpa,
        boolean optimal,
        int evaluatedAssignments,
        LocalDateTime calculatedAt,
        String message) {}
