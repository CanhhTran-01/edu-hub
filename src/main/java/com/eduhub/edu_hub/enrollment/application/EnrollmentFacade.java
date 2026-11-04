package com.eduhub.edu_hub.enrollment.application;

import com.eduhub.edu_hub.enrollment.api.dto.EnrollmentResponse;
import com.eduhub.edu_hub.enrollment.api.dto.LearningHistoryResponse;
import com.eduhub.edu_hub.enrollment.api.dto.ModuleClassUsageResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EnrollmentFacade {
    /** Contract đọc cho scheduling; chưa đăng ký bean cho tới khi có nguồn Enrollment thật. */
    interface Planning {
        /** Toàn bộ lịch sử theo môn/kỳ, kể cả DROPPED/đã ẩn. COMPLETED nghĩa là đã đạt. */
        LearningHistoryResponse findLearningHistory(Set<Long> studentIds);

        /** Đếm mọi đăng ký theo ID lớp, kể cả DROPPED/soft delete. */
        ModuleClassUsageResponse summarizeModuleClass(Long moduleClassId);
    }

    Optional<EnrollmentResponse> findById(Long enrollmentId);

    List<EnrollmentResponse> findByStudentAndSemester(Long studentId, Long semesterId);

    List<EnrollmentResponse> findByModuleClass(Long moduleClassId);

    boolean isEnrolled(Long studentId, Long moduleClassId);

    /** Lấy điểm của sinh viên (dùng cho academic & AI) */
    List<EnrollmentResponse> findCompletedEnrollments(Long studentId);
}
