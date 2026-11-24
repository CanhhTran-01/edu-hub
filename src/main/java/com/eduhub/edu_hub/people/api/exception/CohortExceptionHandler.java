package com.eduhub.edu_hub.people.api.exception;

import com.eduhub.edu_hub.people.api.controller.CohortController;
import com.eduhub.edu_hub.people.application.exception.CohortException;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.MismatchedInputException;

/** Giữ lỗi theo trường và HTTP contract của API khóa, không đưa SQL hoặc stacktrace vào response. */
@RestControllerAdvice(assignableTypes = CohortController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CohortExceptionHandler {
    private final ApiResponseFactory apiResponseFactory;

    @ExceptionHandler(CohortException.class)
    public ResponseEntity<ApiResponse<Void>> handleCohortException(
            CohortException exception, HttpServletRequest request) {
        var errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(apiResponseFactory
                        .<Void>builder()
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        String field = "request";
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof CohortException cohortException)
                return handleCohortException(cohortException, request);
            if (cause instanceof MismatchedInputException inputException
                    && !inputException.getPath().isEmpty()) {
                String inputField = inputException.getPath().getFirst().getPropertyName();
                if (inputField != null) field = inputField;
            }
        }
        return ResponseEntity.badRequest()
                .body(apiResponseFactory.validationError(
                        List.of(new FieldErrorDetail(field, "JSON không hợp lệ hoặc giá trị không đúng định dạng")),
                        request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleCohortDataConflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintException) {
                String constraintName = constraintException.getConstraintName();
                if ("uk_cohorts_start_year".equals(constraintName))
                    return handleCohortException(
                            new CohortException(
                                    ErrorCode.COHORT_ALREADY_EXISTS, "startYear", "Năm bắt đầu đã có khóa tuyển sinh"),
                            request);
                if ("uk_cohorts_code_normalized".equals(constraintName)
                        || "uk_cohorts_code_sequence".equals(constraintName))
                    return handleCohortException(
                            new CohortException(
                                    ErrorCode.COHORT_ALREADY_EXISTS, "cohortCode", "Mã hoặc số thứ tự khóa đã tồn tại"),
                            request);
            }
        }
        return ResponseEntity.internalServerError()
                .body(apiResponseFactory.error(ErrorCode.UNEXPECTED_ERROR, request.getRequestURI()));
    }
}
