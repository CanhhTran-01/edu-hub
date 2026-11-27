package com.eduhub.edu_hub.enrollment.api.dto.request;

import java.math.BigDecimal;

/** Điểm do người dùng nhập để tính thử, không phải yêu cầu đọc hoặc cập nhật lượt học thật. */
public record MinimumExamScoreRequest(
        String subjectName, BigDecimal componentScore1, BigDecimal componentScore2, String targetGradeLetter) {}
