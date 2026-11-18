package com.eduhub.edu_hub.enrollment.api.dto.response;

import java.math.BigDecimal;

/** Một lượt học chưa hủy trên danh sách lớp; các điểm chưa nhập giữ null, ghi chú hiện để trống. */
public record ModuleClassStudentResponse(
        Long enrollmentId,
        Long studentId,
        String studentCode,
        String fullName,
        BigDecimal componentScore1,
        BigDecimal componentScore2,
        BigDecimal processScore,
        String note) {}
