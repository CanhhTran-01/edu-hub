package com.eduhub.edu_hub.people.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.Getter;

/** Lỗi tạo hồ sơ; giữ thông tin trường để controller của People trả lỗi cụ thể. */
@Getter
public class ProfileCreationException extends BaseException {
    private final String field;
    private final String fieldMessage;

    public ProfileCreationException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public ProfileCreationException(ErrorCode errorCode, String field, String fieldMessage) {
        super(errorCode);
        this.field = field;
        this.fieldMessage = fieldMessage;
    }
}
