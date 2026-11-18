package com.eduhub.edu_hub.aiassistant.api.exception;

import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AiAssistantExceptionHandler {
    private final ApiResponseFactory apiResponseFactory;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileTooLarge(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        ErrorCode error = request.getRequestURI().startsWith("/api/v1/ai/")
                ? ErrorCode.AI_DOCUMENT_TOO_LARGE
                : ErrorCode.ADMISSION_IMPORT_INVALID;
        return ResponseEntity.status(error.getHttpStatus())
                .body(apiResponseFactory.error(error, request.getRequestURI()));
    }
}
