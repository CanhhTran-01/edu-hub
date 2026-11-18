package com.eduhub.edu_hub.shared.common;

import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Dựng response với timestamp từ Clock chung; DTO không tự đọc thời gian hoặc giữ bean static. */
@Component
@RequiredArgsConstructor
public class ApiResponseFactory {
    private final Clock clock;

    /** Response thành công và lỗi đều gắn timestamp theo giờ Việt Nam từ Clock chung. */
    public <T> ApiResponse.ApiResponseBuilder<T> builder() {
        return ApiResponse.<T>builder().timestamp(LocalDateTime.now(clock));
    }

    public <T> ApiResponse<T> success(T data) {
        return this.<T>builder().success(true).message("Success").data(data).build();
    }

    public <T> ApiResponse<T> successWithMessage(String message) {
        return this.<T>builder().success(true).message(message).build();
    }

    public <T> ApiResponse<T> error(ErrorCode errorCode, String path) {
        return this.<T>builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .path(path)
                .build();
    }

    public <T> ApiResponse<T> validationError(List<FieldErrorDetail> errors, String path) {
        return this.<T>builder()
                .success(false)
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message(ErrorCode.VALIDATION_ERROR.getMessage())
                .errors(errors)
                .path(path)
                .build();
    }
}
