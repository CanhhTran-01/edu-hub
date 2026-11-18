package com.eduhub.edu_hub.scheduling.api.exception;

import com.eduhub.edu_hub.scheduling.api.controller.PersonalTimetableController;
import com.eduhub.edu_hub.scheduling.api.controller.TimetableController;
import com.eduhub.edu_hub.scheduling.application.exception.TimetableException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {TimetableController.class, PersonalTimetableController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TimetableExceptionHandler {
    private final ApiResponseFactory apiResponseFactory;

    @ExceptionHandler(TimetableException.class)
    public ResponseEntity<ApiResponse<Void>> handleTimetable(TimetableException exception, HttpServletRequest request) {
        var error = exception.getErrorCode();
        return ResponseEntity.status(error.getHttpStatus())
                .body(apiResponseFactory
                        .<Void>builder()
                        .success(false)
                        .code(error.getCode())
                        .message(error.getMessage())
                        .errors(exception.getErrors())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        ErrorCode error = ErrorCode.UNEXPECTED_ERROR;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if ("uk_timetable_plans_active_worker".equals(name)) error = ErrorCode.TIMETABLE_SOLVER_BUSY;
                else if ("chk_class_schedules_room_available".equals(name)
                        || "chk_class_schedules_room_capacity".equals(name)) error = ErrorCode.TIMETABLE_STALE_PLAN;
            }
        }
        return ResponseEntity.status(error.getHttpStatus())
                .body(apiResponseFactory.error(error, request.getRequestURI()));
    }
}
