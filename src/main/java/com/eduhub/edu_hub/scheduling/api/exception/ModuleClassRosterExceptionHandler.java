package com.eduhub.edu_hub.scheduling.api.exception;

import com.eduhub.edu_hub.scheduling.api.controller.ModuleClassRosterController;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassRosterException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ModuleClassRosterController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ModuleClassRosterExceptionHandler {
    private final ApiResponseFactory apiResponseFactory;

    @ExceptionHandler(ModuleClassRosterException.class)
    public ResponseEntity<ApiResponse<Void>> handleRosterException(
            ModuleClassRosterException exception, HttpServletRequest request) {
        var error = exception.getErrorCode();
        return ResponseEntity.status(error.getHttpStatus())
                .body(apiResponseFactory
                        .<Void>builder()
                        .success(false)
                        .code(error.getCode())
                        .message(error.getMessage())
                        .errors(exception.getErrors().isEmpty() ? null : exception.getErrors())
                        .path(request.getRequestURI())
                        .build());
    }
}
