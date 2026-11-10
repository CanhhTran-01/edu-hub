package com.eduhub.edu_hub.people.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.Getter;

/** Lỗi cập nhật hồ sơ giảng viên; handler của People giữ thông tin trường trong response lỗi. */
@Getter
public class LecturerProfileException extends BaseException {
    private final String field;
    private final String fieldMessage;

    public LecturerProfileException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public LecturerProfileException(ErrorCode errorCode, String field, String fieldMessage) {
        super(errorCode);
        this.field = field;
        this.fieldMessage = fieldMessage;
    }
}
