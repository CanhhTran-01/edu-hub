package com.eduhub.edu_hub.admission.api.exception;

import com.eduhub.edu_hub.admission.api.controller.AdministrativeClassController;
import com.eduhub.edu_hub.admission.api.controller.StudentAdmissionController;
import com.eduhub.edu_hub.admission.application.exception.AdmissionEnrollmentException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionImportScopeException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.application.exception.AdmissionSpreadsheetException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {StudentAdmissionController.class, AdministrativeClassController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdmissionExceptionHandler {
    @ExceptionHandler({
        AdmissionPlanException.class,
        AdmissionEnrollmentException.class,
        AdmissionSpreadsheetException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleAdmissionFailure(
            BaseException exception, HttpServletRequest request) {
        var errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode, request.getRequestURI()));
    }

    @ExceptionHandler(AdmissionImportScopeException.class)
    public ResponseEntity<ApiResponse<Void>> handleImportScopeMismatch(
            AdmissionImportScopeException exception, HttpServletRequest request) {

        // TODO(AUDIT_LOG): ghi actor, mã ngành/khóa đã chọn và trong file khi từ chối import;
        //  không lưu nội dung file hoặc CCCD. Việc từ chối không được thay đổi plan.

        var errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .errors(exception.getErrors())
                        .path(request.getRequestURI())
                        .build());
    }
}
