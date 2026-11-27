package com.eduhub.edu_hub.enrollment.api.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Thang quy đổi đã được Enrollment kiểm tra, dùng để xác định miền điểm giả định hợp lệ. */
public record CourseGradeScaleResponse(List<GradeBandResponse> bands) {
    public record GradeBandResponse(BigDecimal minimumScore, String letter, BigDecimal grade4) {}
}
