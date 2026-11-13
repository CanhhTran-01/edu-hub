package com.eduhub.edu_hub.aiassistant.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

public class AiAssistantException extends BaseException {
    public AiAssistantException(ErrorCode errorCode) {
        super(errorCode);
    }
}
