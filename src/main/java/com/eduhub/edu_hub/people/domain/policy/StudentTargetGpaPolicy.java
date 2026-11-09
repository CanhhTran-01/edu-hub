package com.eduhub.edu_hub.people.domain.policy;

import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;

/** Ràng buộc GPA mục tiêu cho cả hồ sơ cá nhân và cập nhật quản trị, không làm tròn dữ liệu nhập. */
public final class StudentTargetGpaPolicy {
    private static final BigDecimal MAXIMUM_TARGET_GPA = new BigDecimal("4");
    private static final int MAXIMUM_FRACTION_DIGITS = 2;

    private StudentTargetGpaPolicy() {}

    public static void validateTargetGpa(BigDecimal targetGpa) {

        // null biểu thị chưa thiết lập mục tiêu; 0 vẫn là một mục tiêu hợp lệ.
        if (targetGpa == null) return;

        if (targetGpa.signum() < 0 || targetGpa.compareTo(MAXIMUM_TARGET_GPA) > 0) {
            throw new StudentProfileException(
                    ErrorCode.INVALID_TARGET_GPA, "targetGpa", "GPA mục tiêu phải từ 0 đến 4");
        }

        if (targetGpa.scale() > MAXIMUM_FRACTION_DIGITS) {
            throw new StudentProfileException(
                    ErrorCode.VALIDATION_ERROR, "targetGpa", "GPA mục tiêu tối đa 2 chữ số thập phân");
        }
    }
}
