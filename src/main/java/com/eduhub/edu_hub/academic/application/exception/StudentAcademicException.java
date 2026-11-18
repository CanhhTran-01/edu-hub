package com.eduhub.edu_hub.academic.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

public class StudentAcademicException extends BaseException {
    public StudentAcademicException(ErrorCode errorCode) {
        super(errorCode);
    }
}
