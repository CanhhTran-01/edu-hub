package com.eduhub.edu_hub.people.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.Getter;

/** Lỗi hồ sơ sinh viên; thông tin field chỉ được chuyển thành lỗi HTTP tại handler của People. */
@Getter
public class StudentProfileException extends BaseException {
    private final String field;
    private final String fieldMessage;

    public StudentProfileException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public StudentProfileException(ErrorCode errorCode, String field, String fieldMessage) {
        super(errorCode);
        this.field = field;
        this.fieldMessage = fieldMessage;
    }
}
