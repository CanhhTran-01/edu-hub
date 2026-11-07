package com.eduhub.edu_hub.admission.application.exception;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

@Getter
public class AdmissionImportScopeException extends BaseException {
    private final List<FieldErrorDetail> errors;

    public AdmissionImportScopeException(List<FieldErrorDetail> errors) {
        super(ErrorCode.ADMISSION_IMPORT_SCOPE_MISMATCH);
        this.errors = List.copyOf(errors);
    }
}
