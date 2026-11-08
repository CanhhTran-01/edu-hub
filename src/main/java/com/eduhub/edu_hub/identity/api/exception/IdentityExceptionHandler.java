package com.eduhub.edu_hub.identity.api.exception;

import com.eduhub.edu_hub.identity.api.controller.AuthController;
import com.eduhub.edu_hub.identity.application.exception.IdentityAccountException;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.exception.PasswordValidationException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AuthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityExceptionHandler {
    @ExceptionHandler(PasswordValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handlePasswordValidation(
            PasswordValidationException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.validationError(exception.getErrors(), request.getRequestURI()));
    }

    @ExceptionHandler({IdentityAuthenticationException.class, IdentityAccountException.class})
    public ResponseEntity<ApiResponse<Void>> handleIdentityException(
            BaseException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(exception.getErrorCode(), request.getRequestURI()));
    }
}
