package com.eduhub.edu_hub.curriculum.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Không tìm thấy ngành chưa xóa khi consumer yêu cầu dữ liệu tham chiếu theo ID. */
public class MajorReferenceException extends BaseException {
    public MajorReferenceException() {
        super(ErrorCode.INVALID_STUDENT_CODE_MAJOR);
    }
}
