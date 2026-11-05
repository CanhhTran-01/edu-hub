package com.eduhub.edu_hub.people.api.exception;

import com.eduhub.edu_hub.people.api.controller.LecturerController;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LecturerController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LecturerCreationExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {

        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                if ("uk_lecturers_email_active".equals(name)) code = ErrorCode.DUPLICATE_LECTURER_EMAIL;
                else if ("uk_lecturers_citizen_id_active".equals(name)) code = ErrorCode.DUPLICATE_LECTURER_CITIZEN_ID;
                else if (name != null && (name.contains("lecturer_code") || name.contains("username")))
                    code = ErrorCode.DUPLICATE_LECTURER_CODE;
            }

            cause = cause.getCause();
        }

        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.error(code, request.getRequestURI()));
    }
}
