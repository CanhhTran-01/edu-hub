package com.eduhub.edu_hub.scheduling.api.exception;

import com.eduhub.edu_hub.scheduling.api.controller.ModuleClassController;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassConflictException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ModuleClassController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ModuleClassExceptionHandler {
    @ExceptionHandler(ModuleClassConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(
            ModuleClassConflictException exception, HttpServletRequest request) {
        var error = exception.getErrorCode();
        return ResponseEntity.status(error.getHttpStatus())
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .code(error.getCode())
                        .message(error.getMessage())
                        .errors(exception.getErrors())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        String constraint = "";
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                constraint = violation.getConstraintName();
                break;
            }
        }
        var error =
                switch (constraint) {
                    case "uk_module_classes_code", "uk_module_classes_sequence" -> ErrorCode.MODULE_CLASS_CODE_CONFLICT;
                    case "chk_module_classes_sequence",
                            "chk_module_classes_capacity",
                            "chk_module_classes_current_size",
                            "chk_module_classes_status" -> ErrorCode.VALIDATION_ERROR;
                    case "fk_module_classes_semester" -> ErrorCode.SEMESTER_NOT_FOUND;
                    case "fk_module_classes_subject" -> ErrorCode.SUBJECT_NOT_FOUND;
                    default -> ErrorCode.UNEXPECTED_ERROR;
                };
        if (error == ErrorCode.UNEXPECTED_ERROR)
            log.error("Unexpected module class database error at [{}]", request.getRequestURI(), exception);
        else log.warn("Module class constraint rejected: constraint={}, path={}", constraint, request.getRequestURI());
        return ResponseEntity.status(error.getHttpStatus()).body(ApiResponse.error(error, request.getRequestURI()));
    }
}
