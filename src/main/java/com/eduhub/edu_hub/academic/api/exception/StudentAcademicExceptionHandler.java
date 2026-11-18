package com.eduhub.edu_hub.academic.api.exception;

import com.eduhub.edu_hub.academic.api.controller.StudentAcademicController;
import com.eduhub.edu_hub.academic.application.exception.StudentAcademicException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = StudentAcademicController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StudentAcademicExceptionHandler {
    @ExceptionHandler(StudentAcademicException.class)
    public ResponseEntity<ApiResponse<Void>> handleAcademic(
            StudentAcademicException exception, HttpServletRequest request) {
        var error = exception.getErrorCode();
        return ResponseEntity.status(error.getHttpStatus()).body(ApiResponse.error(error, request.getRequestURI()));
    }
}
