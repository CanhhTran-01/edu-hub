package com.eduhub.edu_hub.aiassistant.application.exception;

/** Lỗi cấu hình AI cần được sửa trước khi khởi động; thông báo không chứa khóa truy cập. */
public class AiAssistantConfigurationException extends IllegalStateException {
    public AiAssistantConfigurationException(String message) {
        super(message);
    }
}
