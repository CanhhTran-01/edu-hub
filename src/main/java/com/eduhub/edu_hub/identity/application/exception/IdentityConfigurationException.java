package com.eduhub.edu_hub.identity.application.exception;

/** Cấu hình hoặc dữ liệu khởi tạo Identity không cho phép ứng dụng khởi động an toàn. */
public class IdentityConfigurationException extends IllegalStateException {
    public IdentityConfigurationException(String message) {
        super(message);
    }

    public IdentityConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
