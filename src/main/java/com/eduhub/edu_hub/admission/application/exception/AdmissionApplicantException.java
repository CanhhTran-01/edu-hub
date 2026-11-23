package com.eduhub.edu_hub.admission.application.exception;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

/** Lỗi sửa hồ sơ nhập học; giữ field errors để FE đặt lỗi đúng trên form. */
@Getter
public class AdmissionApplicantException extends BaseException {
    private final List<FieldErrorDetail> errors;

    public AdmissionApplicantException(ErrorCode errorCode, List<FieldErrorDetail> errors) {
        super(errorCode);
        this.errors = List.copyOf(errors);
    }
}
