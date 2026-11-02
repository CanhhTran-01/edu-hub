package com.eduhub.edu_hub.shared.exception;

import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .headers(exception.getHeaders())
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED, request.getRequestURI()));
    }

    // Thiếu tham số cho URL
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handlingMissingServletRequestParameterException(
            MissingServletRequestParameterException exception, HttpServletRequest request) {

        log.warn("Missing parameter at [{}]: {}", request.getRequestURI(), exception.getMessage());

        return ResponseEntity.status(ErrorCode.MISSING_PARAMETER.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.MISSING_PARAMETER, request.getRequestURI()));
    }

    // Validation exception
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handlingMethodArgumentNotValidException(
            MethodArgumentNotValidException exception, HttpServletRequest request) {

        List<FieldErrorDetail> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();

        log.warn("Validation failed at [{}]: {}", request.getRequestURI(), errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError(errors, request.getRequestURI()));
    }

    // Business exception
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handlingBaseException(
            BaseException exception, HttpServletRequest request) {

        log.error(
                "Business Exception: code={}, message={}, path={}",
                exception.getErrorCode().getCode(),
                exception.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(exception.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(exception.getErrorCode(), request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handlingHttpMessageNotReadableException(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        log.warn("Invalid request body at [{}]", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, request.getRequestURI()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handlingConstraintViolationException(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldErrorDetail> errors = exception.getConstraintViolations().stream()
                .map(violation ->
                        new FieldErrorDetail(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError(errors, request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.validationError(
                        List.of(new FieldErrorDetail(exception.getName(), "Giá trị tham số không hợp lệ")),
                        request.getRequestURI()));
    }

    // Fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(Exception exception, HttpServletRequest request) {

        log.error("Unexpected Exception at [{}]: ", request.getRequestURI(), exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.UNEXPECTED_ERROR, request.getRequestURI()));
    }
}
