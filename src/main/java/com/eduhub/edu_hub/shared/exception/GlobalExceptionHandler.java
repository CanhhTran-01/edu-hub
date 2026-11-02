package com.eduhub.edu_hub.shared.exception;

import com.eduhub.edu_hub.curriculum.api.dto.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.exception.PrerequisiteCycleException;
import com.eduhub.edu_hub.curriculum.application.exception.SubjectHasDependentsException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

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

    @ExceptionHandler(PrerequisiteCycleException.class)
    public ResponseEntity<ApiResponse<Void>> handlePrerequisiteCycle(
            PrerequisiteCycleException exception, HttpServletRequest request) {

        String cycle = exception.getCycle().stream()
                .map(SubjectSimpleResponse::getSubjectCode)
                .collect(Collectors.joining(" → "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .code(ErrorCode.PREREQUISITE_CYCLE.getCode())
                        .message("Phát hiện chu trình tiên quyết: " + cycle)
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(SubjectHasDependentsException.class)
    public ResponseEntity<ApiResponse<Void>> handleSubjectHasDependents(
            SubjectHasDependentsException exception, HttpServletRequest request) {

        var errors = exception.getDependentSubjects().stream()
                .map(subject ->
                        new FieldErrorDetail("requiredBy", subject.getSubjectCode() + " - " + subject.getSubjectName()))
                .toList();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .code(ErrorCode.SUBJECT_IN_USE.getCode())
                        .message("Học phần là tiên quyết của học phần đang sử dụng. Hãy gỡ ràng buộc tại UC-21 trước.")
                        .errors(errors)
                        .path(request.getRequestURI())
                        .build());
    }

    // Fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(Exception exception, HttpServletRequest request) {

        log.error("Unexpected Exception at [{}]: ", request.getRequestURI(), exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.UNEXPECTED_ERROR, request.getRequestURI()));
    }
}
