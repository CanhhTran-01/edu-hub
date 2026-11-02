package com.eduhub.edu_hub.scheduling.api.exception;

import com.eduhub.edu_hub.scheduling.api.controller.SemesterController;
import com.eduhub.edu_hub.scheduling.application.exception.SemesterConflictException;
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

@RestControllerAdvice(assignableTypes = SemesterController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class SemesterExceptionHandler {
    @ExceptionHandler(SemesterConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(
            SemesterConflictException exception, HttpServletRequest request) {
        ErrorCode error = exception.getErrorCode();
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
        ErrorCode error =
                switch (constraint) {
                    case "uk_semesters_name", "uk_semesters_code" -> ErrorCode.DUPLICATE_SEMESTER_NAME;
                    case "ex_semesters_study_period" -> ErrorCode.SEMESTER_DATE_OVERLAP;
                    case "ex_semesters_registration_period" -> ErrorCode.SEMESTER_REGISTRATION_OVERLAP;
                    case "chk_semesters_name", "chk_semesters_code" -> ErrorCode.INVALID_SEMESTER_NAME;
                    case "chk_semesters_study_dates" -> ErrorCode.INVALID_SEMESTER_DATES;
                    case "chk_semesters_registration_dates" -> ErrorCode.INVALID_REGISTRATION_PERIOD;
                    default -> ErrorCode.UNEXPECTED_ERROR;
                };
        if (error == ErrorCode.UNEXPECTED_ERROR) {
            log.error("Unexpected semester database error at [{}]", request.getRequestURI(), exception);
        } else {
            log.warn("Semester constraint rejected: constraint={}, path={}", constraint, request.getRequestURI());
        }
        return ResponseEntity.status(error.getHttpStatus()).body(ApiResponse.error(error, request.getRequestURI()));
    }
}
