package com.eduhub.edu_hub.academic.application.exception;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

@Getter
public class GpaSimulationException extends BaseException {
    private final List<FieldErrorDetail> fieldErrors;

    public GpaSimulationException(ErrorCode errorCode) {
        super(errorCode);
        this.fieldErrors = List.of();
    }

    public GpaSimulationException(ErrorCode errorCode, Throwable cause) {
        this(errorCode);
        initCause(cause);
    }

    public GpaSimulationException(ErrorCode errorCode, String field, String message) {
        super(errorCode);
        this.fieldErrors = List.of(new FieldErrorDetail(field, message));
    }
}
