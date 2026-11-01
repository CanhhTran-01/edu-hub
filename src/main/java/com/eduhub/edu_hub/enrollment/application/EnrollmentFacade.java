package com.eduhub.edu_hub.enrollment.application;

import com.eduhub.edu_hub.enrollment.api.dto.EnrollmentResponse;
import java.util.List;
import java.util.Optional;

public interface EnrollmentFacade {

    Optional<EnrollmentResponse> findById(Long enrollmentId);

    List<EnrollmentResponse> findByStudentAndSemester(Long studentId, Long semesterId);

    List<EnrollmentResponse> findByModuleClass(Long moduleClassId);

    boolean isEnrolled(Long studentId, Long moduleClassId);

    /** Lấy điểm của sinh viên (dùng cho academic & AI) */
    List<EnrollmentResponse> findCompletedEnrollments(Long studentId);
}
