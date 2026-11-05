package com.eduhub.edu_hub.identity.application.exception;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import java.util.List;
import lombok.Getter;

@Getter
public class PasswordValidationException extends RuntimeException {
    private final List<FieldErrorDetail> errors;

    public PasswordValidationException(List<FieldErrorDetail> errors) {
        this.errors = List.copyOf(errors);
    }
}
