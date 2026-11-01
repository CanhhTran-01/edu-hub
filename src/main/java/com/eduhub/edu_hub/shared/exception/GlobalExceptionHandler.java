package com.eduhub.edu_hub.shared.exception;

import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 0. Thiếu tham số cho URL
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handlingMissingServletRequestParameterException(
            MissingServletRequestParameterException exception, HttpServletRequest request) {

        log.warn("Missing parameter at [{}]: {}", request.getRequestURI(), exception.getMessage());

        return ResponseEntity.status(ErrorCode.MISSING_PARAMETER.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.MISSING_PARAMETER, request.getRequestURI()));
    }

    // 1. Validation exception
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

    // 2. Business exception
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

    // 3. Fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(Exception exception, HttpServletRequest request) {

        log.error("Unexpected Exception at [{}]: ", request.getRequestURI(), exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.UNEXPECTED_ERROR, request.getRequestURI()));
    }
}
