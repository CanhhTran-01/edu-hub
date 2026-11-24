package com.eduhub.edu_hub.curriculum.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi thành phần đầu vào khi cấp mã CTĐT. */
public class CurriculumCodeException extends BaseException {
    public CurriculumCodeException() {
        super(ErrorCode.VALIDATION_ERROR);
    }
}
