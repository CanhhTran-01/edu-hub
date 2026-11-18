package com.eduhub.edu_hub.scheduling.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi mở, quản lý lớp học phần và dữ liệu phục vụ tính nhu cầu mở lớp. */
public class ModuleClassException extends BaseException {
    public ModuleClassException(ErrorCode errorCode) {
        super(errorCode);
    }
}
