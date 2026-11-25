package com.eduhub.edu_hub.enrollment.application.facade;

import com.eduhub.edu_hub.enrollment.api.dto.response.EnrollmentResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.EnrollmentScopeResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.LearningHistoryResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.ModuleClassStudentResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.ModuleClassUsageResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EnrollmentFacade {
    interface StudentRecords {
        StudentLearningHistoryResponse findLearningHistory(Long studentId);
    }

    interface ClassRoster {
        /** REGISTERED/COMPLETED/FAILED chưa xóa, kèm danh tính hiện tại và điểm đã lưu; không gọi ngược Scheduling. */
        List<ModuleClassStudentResponse> findStudentsByModuleClassId(Long moduleClassId);
    }

    /** Đọc phạm vi và lịch sử cho Scheduling qua EnrollmentPlanningFacadeImpl. */
    interface Planning {
        EnrollmentScopeResponse findScope();
        /** Toàn bộ lịch sử theo môn/kỳ, kể cả DROPPED/đã ẩn. COMPLETED nghĩa là đã đạt. */
        LearningHistoryResponse findLearningHistory(Set<Long> studentIds);

        /** Đếm mọi đăng ký theo ID lớp, kể cả DROPPED/soft delete. */
        ModuleClassUsageResponse summarizeModuleClass(Long moduleClassId);
    }

    interface RegistrationUsage {
        long countActiveRegistrations(Long moduleClassId);
    }

    // TODO(ENROLLMENT_FACADE_QUERIES): Các truy vấn trực tiếp dưới đây chưa có implementation hoặc consumer;
    // chỉ bổ sung khi có use-case thực tế, các consumer hiện tại dùng những capability bên trên.
    Optional<EnrollmentResponse> findById(Long enrollmentId);

    List<EnrollmentResponse> findByStudentAndSemester(Long studentId, Long semesterId);

    List<EnrollmentResponse> findByModuleClass(Long moduleClassId);

    boolean isEnrolled(Long studentId, Long moduleClassId);

    /** Lấy điểm của sinh viên (dùng cho academic & AI) */
    List<EnrollmentResponse> findCompletedEnrollments(Long studentId);
}
