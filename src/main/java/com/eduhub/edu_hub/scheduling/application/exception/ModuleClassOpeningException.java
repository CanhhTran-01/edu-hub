package com.eduhub.edu_hub.scheduling.application.exception;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;

public class ModuleClassOpeningException extends ModuleClassConflictException {
    public ModuleClassOpeningException(ErrorCode errorCode, List<FieldErrorDetail> errors) {
        super(errorCode, errors);
    }
}
