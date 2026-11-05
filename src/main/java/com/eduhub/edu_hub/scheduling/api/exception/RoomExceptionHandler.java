package com.eduhub.edu_hub.scheduling.api.exception;

import com.eduhub.edu_hub.scheduling.api.controller.RoomController;
import com.eduhub.edu_hub.scheduling.application.exception.RoomConflictException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RoomController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RoomExceptionHandler {
    @ExceptionHandler(RoomConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(
            RoomConflictException exception, HttpServletRequest request) {
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
        ErrorCode error =
                switch (constraint) {
                    case "uk_rooms_code" -> ErrorCode.DUPLICATE_ROOM_CODE;
                    case "uk_rooms_location" -> ErrorCode.DUPLICATE_ROOM_LOCATION;
                    case "chk_rooms_capacity",
                            "chk_rooms_status",
                            "chk_rooms_identifiers",
                            "chk_rooms_details" -> ErrorCode.VALIDATION_ERROR;
                    default -> ErrorCode.UNEXPECTED_ERROR;
                };
        return ResponseEntity.status(error.getHttpStatus()).body(ApiResponse.error(error, request.getRequestURI()));
    }
}
