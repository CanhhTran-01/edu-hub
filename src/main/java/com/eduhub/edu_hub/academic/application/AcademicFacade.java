package com.eduhub.edu_hub.academic.application;

import com.eduhub.edu_hub.academic.application.facade.dto.StudentAcademicSummaryResponse;
import java.math.BigDecimal;

public interface AcademicFacade {

    /** Tính GPA tích lũy hiện tại */
    BigDecimal calculateCumulativeGpa(Long studentId);

    /** Tính số tín chỉ tích lũy */
    int calculateAccumulatedCredits(Long studentId);

    /** Lấy tóm tắt học vụ của sinh viên */
    StudentAcademicSummaryResponse getStudentAcademicSummary(Long studentId);

    /** Kiểm tra và cập nhật cảnh báo học vụ */
    // TODO(ACADEMIC_WARNING_EVALUATION): Chưa có implementation; chỉ triển khai sau khi có nguồn điểm,
    // lịch sử đăng ký/kết quả học tập đầy đủ và quy chế cảnh báo đã được thống nhất.
    // Cần xét nhiều thông tin như GPA học kỳ/tích lũy thực tế, số tín chỉ học lại, tín chỉ nợ/không đạt,
    // kỳ xét và các tiêu chí khác theo quy chế; chưa tự đặt ngưỡng hoặc suy cảnh báo từ targetGpa.
    // Academic điều phối qua facade của module sở hữu dữ liệu và cập nhật đồng thời trạng thái/lý do
    // cảnh báo qua People trong transaction phù hợp; không nhận kết luận cảnh báo do client nhập tay.
    void evaluateAcademicWarning(Long studentId);
}
