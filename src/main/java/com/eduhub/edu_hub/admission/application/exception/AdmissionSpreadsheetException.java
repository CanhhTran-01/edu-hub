package com.eduhub.edu_hub.admission.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi cấu trúc, metadata hoặc thao tác đọc/ghi bảng tính nhập học. */
public class AdmissionSpreadsheetException extends BaseException {
    public AdmissionSpreadsheetException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AdmissionSpreadsheetException(ErrorCode errorCode, Throwable cause) {
        this(errorCode);
        initCause(cause);
    }
}
