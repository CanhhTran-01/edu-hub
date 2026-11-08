package com.eduhub.edu_hub.identity.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi nghiệp vụ khi cấp, tra cứu hoặc quản trị tài khoản thuộc Identity. */
public class IdentityAccountException extends BaseException {
    public IdentityAccountException(ErrorCode errorCode) {
        super(errorCode);
    }
}
