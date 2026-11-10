package com.eduhub.edu_hub.people.api.exception;

import com.eduhub.edu_hub.curriculum.application.exception.TeachingSubjectException;
import com.eduhub.edu_hub.people.api.controller.AdminLecturerController;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminLecturerController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LecturerCreationExceptionHandler {
    @ExceptionHandler(ProfileCreationException.class)
    public ResponseEntity<ApiResponse<Void>> handleProfileCreationException(
            ProfileCreationException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .errors(
                                exception.getField() == null
                                        ? null
                                        : List.of(new FieldErrorDetail(
                                                exception.getField(), exception.getFieldMessage())))
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(TeachingSubjectException.class)
    public ResponseEntity<ApiResponse<Void>> handleTeachingSubjectException(
            TeachingSubjectException exception, HttpServletRequest request) {
        return handleProfileCreationException(
                new ProfileCreationException(
                        exception.getErrorCode(),
                        "teachingSubjectIds",
                        exception.getErrorCode().getMessage()),
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {

        ErrorCode code = ErrorCode.UNEXPECTED_ERROR;
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

        String field =
                switch (code) {
                    case DUPLICATE_LECTURER_EMAIL -> "email";
                    case DUPLICATE_LECTURER_CITIZEN_ID -> "citizenId";
                    default -> null;
                };
        return handleProfileCreationException(new ProfileCreationException(code, field, code.getMessage()), request);
    }
}
