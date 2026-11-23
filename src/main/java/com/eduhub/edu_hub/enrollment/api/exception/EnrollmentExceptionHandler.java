package com.eduhub.edu_hub.enrollment.api.exception;

import com.eduhub.edu_hub.enrollment.api.controller.CourseGradeController;
import com.eduhub.edu_hub.enrollment.api.controller.EnrollmentController;
import com.eduhub.edu_hub.enrollment.api.controller.ProcessScoreController;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.exc.MismatchedInputException;

@RestControllerAdvice(
        assignableTypes = {EnrollmentController.class, ProcessScoreController.class, CourseGradeController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@lombok.extern.slf4j.Slf4j
@RequiredArgsConstructor
public class EnrollmentExceptionHandler {
    private final ApiResponseFactory apiResponseFactory;

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof EnrollmentException enrollmentException)
                return handleEnrollment(enrollmentException, request);
            if (cause instanceof MismatchedInputException inputException
                    && !inputException.getPath().isEmpty()) {
                String field = inputException.getPath().stream()
                        .map(reference -> reference.getPropertyName() == null
                                ? "[" + reference.getIndex() + "]"
                                : "." + reference.getPropertyName())
                        .collect(java.util.stream.Collectors.joining());
                if (field.startsWith(".")) field = field.substring(1);
                return ResponseEntity.badRequest()
                        .body(apiResponseFactory.validationError(
                                List.of(new FieldErrorDetail(field, "Giá trị trường không đúng định dạng")),
                                request.getRequestURI()));
            }
        }
        return ResponseEntity.badRequest()
                .body(apiResponseFactory.error(ErrorCode.VALIDATION_ERROR, request.getRequestURI()));
    }

    @ExceptionHandler({
        org.springframework.web.multipart.MultipartException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidUpload(Exception exception, HttpServletRequest request) {
        ErrorCode uploadError = request.getRequestURI().contains("/course-grades/")
                ? ErrorCode.COURSE_GRADE_IMPORT_INVALID
                : ErrorCode.PROCESS_SCORE_IMPORT_INVALID;
        return ResponseEntity.badRequest()
                .body(apiResponseFactory
                        .<Void>builder()
                        .success(false)
                        .code(uploadError.getCode())
                        .message(uploadError.getMessage())
                        .errors(List.of(new FieldErrorDetail("file", "Cần tệp XLSX hợp lệ, tối đa 5 MB")))
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(EnrollmentException.class)
    public ResponseEntity<ApiResponse<Void>> handleEnrollment(
            EnrollmentException exception, HttpServletRequest request) {
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
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uk_enrollments_active_subject_semester".equals(violation.getConstraintName())) {
                error = ErrorCode.ALREADY_ENROLLED;
                break;
            }
        }
        if (error == ErrorCode.UNEXPECTED_ERROR)
            log.error("Không lưu được lượt học: path={}", request.getRequestURI(), exception);
        return ResponseEntity.status(error.getHttpStatus())
                .body(apiResponseFactory.error(error, request.getRequestURI()));
    }
}
