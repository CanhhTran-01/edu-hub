package com.eduhub.edu_hub.curriculum.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Tập môn được chọn không đủ điều kiện ghi nhận năng lực giảng dạy. */
public class TeachingSubjectException extends BaseException {
    public TeachingSubjectException(ErrorCode errorCode) {
        super(errorCode);
    }
}
