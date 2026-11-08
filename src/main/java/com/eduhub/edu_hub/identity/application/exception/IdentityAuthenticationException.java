package com.eduhub.edu_hub.identity.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi xác thực tài khoản, token hoặc phiên; giữ mã lỗi để HTTP handler ánh xạ đúng contract. */
public class IdentityAuthenticationException extends BaseException {
    public IdentityAuthenticationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public IdentityAuthenticationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}
