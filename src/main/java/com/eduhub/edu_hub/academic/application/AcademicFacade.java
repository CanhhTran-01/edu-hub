package com.eduhub.edu_hub.academic.application;

import com.eduhub.edu_hub.academic.api.dto.StudentAcademicSummaryResponse;
import java.math.BigDecimal;

public interface AcademicFacade {

    /** Tính GPA tích lũy hiện tại */
    BigDecimal calculateCumulativeGpa(Long studentId);

    /** Tính số tín chỉ tích lũy */
    int calculateAccumulatedCredits(Long studentId);

    /** Lấy tóm tắt học vụ của sinh viên */
    StudentAcademicSummaryResponse getStudentAcademicSummary(Long studentId);

    /** Kiểm tra và cập nhật cảnh báo học vụ */
    void evaluateAcademicWarning(Long studentId);
}
