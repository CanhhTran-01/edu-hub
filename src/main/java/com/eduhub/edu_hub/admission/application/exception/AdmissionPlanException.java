package com.eduhub.edu_hub.admission.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi cấu hình, trạng thái, phiên bản hoặc phân bổ của phương án nhập học. */
public class AdmissionPlanException extends BaseException {
    public AdmissionPlanException(ErrorCode errorCode) {
        super(errorCode);
    }
}
