package com.eduhub.edu_hub.scheduling.application.exception;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

@Getter
public class RoomConflictException extends BaseException {
    private final List<FieldErrorDetail> errors;

    public RoomConflictException(ErrorCode errorCode, List<FieldErrorDetail> errors) {
        super(errorCode);
        this.errors = List.copyOf(errors);
    }
}
