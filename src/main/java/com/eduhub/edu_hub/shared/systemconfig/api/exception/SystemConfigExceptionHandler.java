package com.eduhub.edu_hub.shared.systemconfig.api.exception;

import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Lỗi nguồn cấu hình có thể phát sinh tại mọi consumer; handler chỉ nhận đúng exception cấu hình chung. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SystemConfigExceptionHandler {
    private final ApiResponseFactory apiResponseFactory;

    @ExceptionHandler(SystemConfigException.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemConfigException(
            SystemConfigException exception, HttpServletRequest request) {
        var error = exception.getErrorCode();
        return ResponseEntity.status(error.getHttpStatus())
                .body(apiResponseFactory
                        .<Void>builder()
                        .success(false)
                        .code(error.getCode())
                        .message(error.getMessage())
                        .errors(exception.getFieldErrors().isEmpty() ? null : exception.getFieldErrors())
                        .path(request.getRequestURI())
                        .build());
    }
}
