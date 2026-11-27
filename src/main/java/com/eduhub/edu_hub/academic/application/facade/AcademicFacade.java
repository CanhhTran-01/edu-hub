package com.eduhub.edu_hub.academic.application.facade;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest;
import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest;
import com.eduhub.edu_hub.academic.api.dto.request.PrerequisiteImpactRequest;
import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaGoalResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationPreviewResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationSnapshotResponse;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AcademicFacade {
    interface Prerequisites {
        PrerequisiteImpactResponse analyzeMyPrerequisites(Jwt principal, PrerequisiteImpactRequest request);
    }

    interface GpaSimulation {
        GpaSimulationSnapshotResponse findMySnapshot(Jwt principal);

        GpaSimulationPreviewResponse previewMyGpa(Jwt principal, GpaSimulationRequest request);

        GpaGoalResponse solveMyGpaGoal(Jwt principal, GpaGoalRequest request);
    }

    /** Chỉ đọc học vụ của principal đã xác thực, không nhận Student ID từ model hoặc client. */
    interface Advisor {
        AdvisorAcademicProfileResponse findMyAcademicProfile(Jwt principal);
    }

    /** Kiểm tra và cập nhật cảnh báo học vụ */
    // TODO(ACADEMIC_WARNING_EVALUATION): Chưa có implementation; chỉ triển khai sau khi có nguồn điểm,
    //  lịch sử đăng ký/kết quả học tập đầy đủ và quy chế cảnh báo đã được thống nhất.
    //  Cần xét nhiều thông tin như GPA học kỳ/tích lũy thực tế, số tín chỉ học lại, tín chỉ nợ/không đạt,
    //  kỳ xét và các tiêu chí khác theo quy chế; chưa tự đặt ngưỡng hoặc suy cảnh báo từ targetGpa.
    //  Academic điều phối qua facade của module sở hữu dữ liệu và cập nhật đồng thời trạng thái/lý do
    //  cảnh báo qua People trong transaction phù hợp; không nhận kết luận cảnh báo do client nhập tay.
    void evaluateAcademicWarning(Long studentId);
}
