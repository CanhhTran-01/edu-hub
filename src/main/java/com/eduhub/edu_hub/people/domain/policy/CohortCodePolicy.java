package com.eduhub.edu_hub.people.domain.policy;

import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Ghép mã khóa từ STT đã được cấp, không suy STT từ năm học hoặc số lượng bản ghi. */
public final class CohortCodePolicy {
    private CohortCodePolicy() {}

    // TODO(COHORT_CODE_CREATION): Chưa có luồng tạo khóa; khi bổ sung, service phải cấp STT nguyên tử,
    // giữ số đã dùng kể cả xóa mềm rồi gọi policy trong cùng transaction. Dữ liệu seed xử lý ở task riêng.
    public static String generateCode(int cohortSequence) {
        if (cohortSequence < 1 || cohortSequence > 99)
            throw new ProfileCreationException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        return "K" + cohortSequence;
    }
}
