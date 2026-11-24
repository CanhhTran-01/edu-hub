package com.eduhub.edu_hub.admission.api.exception;

import com.eduhub.edu_hub.admission.api.controller.AdmissionController;
import com.eduhub.edu_hub.admission.api.controller.AdmissionNotificationController;
import com.eduhub.edu_hub.admission.api.controller.AdmissionPlanAdjustmentController;
import com.eduhub.edu_hub.admission.application.exception.AdmissionApplicantException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionEnrollmentException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionImportScopeException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionSpreadsheetException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = {
            AdmissionController.class,
            AdmissionPlanAdjustmentController.class,
            AdmissionNotificationController.class
        })
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AdmissionExceptionHandler {
    private final ApiResponseFactory apiResponseFactory;

    @ExceptionHandler({
        AdmissionPlanException.class,
        AdmissionEnrollmentException.class,
        AdmissionSpreadsheetException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleAdmissionFailure(
            BaseException exception, HttpServletRequest request) {
        var errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(apiResponseFactory.error(errorCode, request.getRequestURI()));
    }

    @ExceptionHandler(AdmissionImportScopeException.class)
    public ResponseEntity<ApiResponse<Void>> handleImportScopeMismatch(
            AdmissionImportScopeException exception, HttpServletRequest request) {

        // TODO(AUDIT_LOG): ghi actor, mã ngành/khóa đã chọn và trong file khi từ chối import;
        //  không lưu nội dung file hoặc CCCD. Việc từ chối không được thay đổi plan.

        var errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(apiResponseFactory
                        .<Void>builder()
                        .success(false)
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .errors(exception.getErrors())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(AdmissionApplicantException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicantValidation(
            AdmissionApplicantException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(apiResponseFactory.validationError(exception.getErrors(), request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJson(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AdmissionApplicantException applicantException)
                return handleApplicantValidation(applicantException, request);
        }
        return ResponseEntity.badRequest()
                .body(apiResponseFactory.error(ErrorCode.VALIDATION_ERROR, request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabaseConflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("uk_admission_plans_active_scope")) {
                var errorCode = ErrorCode.ADMISSION_PLAN_ALREADY_EXISTS;
                return ResponseEntity.status(errorCode.getHttpStatus())
                        .body(apiResponseFactory.error(errorCode, request.getRequestURI()));
            }
        }
        return ResponseEntity.internalServerError()
                .body(apiResponseFactory.error(ErrorCode.UNEXPECTED_ERROR, request.getRequestURI()));
    }
}
