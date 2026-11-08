package com.eduhub.edu_hub.admission.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi điều kiện xác nhận hoặc lưu kết quả nhập học của từng hồ sơ. */
public class AdmissionEnrollmentException extends BaseException {
    public AdmissionEnrollmentException(ErrorCode errorCode) {
        super(errorCode);
    }
}
