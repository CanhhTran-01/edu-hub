package com.eduhub.edu_hub.people.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.Getter;

/** Lỗi tạo khóa; thông tin trường được HTTP handler của People chuyển thành response. */
@Getter
public class CohortException extends BaseException {
    private final String field;
    private final String fieldMessage;

    public CohortException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public CohortException(ErrorCode errorCode, String field, String fieldMessage) {
        super(errorCode);
        this.field = field;
        this.fieldMessage = fieldMessage;
    }
}
