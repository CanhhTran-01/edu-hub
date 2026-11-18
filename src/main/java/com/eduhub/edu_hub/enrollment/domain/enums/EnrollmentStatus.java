package com.eduhub.edu_hub.enrollment.domain.enums;

public enum EnrollmentStatus {
    // TODO(LEARNING_ATTEMPT_STATUS): Đổi tên enum thành LearningAttemptStatus để thể hiện trạng thái
    //  một lượt học; cập nhật đồng bộ nơi sử dụng và giữ nguyên giá trị SQL/JSON khi thực hiện.
    REGISTERED,
    DROPPED,
    COMPLETED,
    FAILED
}
