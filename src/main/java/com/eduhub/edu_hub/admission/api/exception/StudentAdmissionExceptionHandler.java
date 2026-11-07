package com.eduhub.edu_hub.admission.api.exception;

import com.eduhub.edu_hub.admission.api.controller.StudentAdmissionController;
import com.eduhub.edu_hub.admission.application.exception.AdmissionImportScopeException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = StudentAdmissionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StudentAdmissionExceptionHandler {
    @ExceptionHandler(AdmissionImportScopeException.class)
    public ResponseEntity<ApiResponse<Void>> importScopeMismatch(
            AdmissionImportScopeException exception, HttpServletRequest request) {
        // TODO(AUDIT_LOG): ghi actor, mã ngành/khóa đã chọn và trong file khi từ chối import;
        // không lưu nội dung file hoặc CCCD. Việc từ chối không được thay đổi plan.
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
}
