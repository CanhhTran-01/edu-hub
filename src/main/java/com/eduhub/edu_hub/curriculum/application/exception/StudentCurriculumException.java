package com.eduhub.edu_hub.curriculum.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

public class StudentCurriculumException extends BaseException {
    public StudentCurriculumException(ErrorCode errorCode) {
        super(errorCode);
    }
}
