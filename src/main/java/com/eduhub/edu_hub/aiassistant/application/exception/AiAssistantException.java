package com.eduhub.edu_hub.aiassistant.application.exception;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

@Getter
public class AiAssistantException extends BaseException {
    private final List<FieldErrorDetail> fieldErrors;

    public AiAssistantException(ErrorCode errorCode) {
        super(errorCode);
        this.fieldErrors = List.of();
    }

    public AiAssistantException(ErrorCode errorCode, String field, String message) {
        super(errorCode);
        this.fieldErrors = List.of(new FieldErrorDetail(field, message));
    }
}
