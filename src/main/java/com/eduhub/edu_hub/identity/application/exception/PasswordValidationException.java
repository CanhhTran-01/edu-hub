package com.eduhub.edu_hub.identity.application.exception;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

@Getter
public class PasswordValidationException extends IdentityAuthenticationException {
    private final List<FieldErrorDetail> errors;

    public PasswordValidationException(List<FieldErrorDetail> errors) {
        super(ErrorCode.VALIDATION_ERROR);
        this.errors = List.copyOf(errors);
    }
}
