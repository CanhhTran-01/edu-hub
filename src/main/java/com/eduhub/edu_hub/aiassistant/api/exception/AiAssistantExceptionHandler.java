package com.eduhub.edu_hub.aiassistant.api.exception;

import com.eduhub.edu_hub.academic.application.exception.GpaSimulationException;
import com.eduhub.edu_hub.aiassistant.api.controller.AiChatController;
import com.eduhub.edu_hub.aiassistant.api.controller.AiChatSessionController;
import com.eduhub.edu_hub.aiassistant.api.controller.AiGpaSimulationController;
import com.eduhub.edu_hub.aiassistant.api.controller.KnowledgeSourceController;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(
        assignableTypes = {
            AiChatController.class,
            AiChatSessionController.class,
            KnowledgeSourceController.class,
            AiGpaSimulationController.class
        })
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AiAssistantExceptionHandler {
    private static final String ADVISOR_RETRY_AFTER_SECONDS = "10";
    private final ApiResponseFactory apiResponseFactory;

    @ExceptionHandler(GpaSimulationException.class)
    public ResponseEntity<ApiResponse<Void>> handleSimulationException(
            GpaSimulationException exception, HttpServletRequest request) {
        var error = exception.getErrorCode();
        return ResponseEntity.status(error.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(apiResponseFactory
                        .<Void>builder()
                        .success(false)
                        .code(error.getCode())
                        .message(error.getMessage())
                        .path(request.getRequestURI())
                        .errors(exception.getFieldErrors().isEmpty() ? null : exception.getFieldErrors())
                        .build());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileTooLarge(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        ErrorCode error = ErrorCode.AI_DOCUMENT_TOO_LARGE;
        return ResponseEntity.status(error.getHttpStatus())
                .body(apiResponseFactory.error(error, request.getRequestURI()));
    }

    @ExceptionHandler(AiAssistantException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiException(
            AiAssistantException exception, HttpServletRequest request) {
        var error = exception.getErrorCode();
        // SSE client chỉ Accept text/event-stream vẫn phải nhận lỗi JSON trước khi stream mở.
        var response = ResponseEntity.status(error.getHttpStatus()).contentType(MediaType.APPLICATION_JSON);
        if (error == ErrorCode.AI_RATE_LIMIT_EXCEEDED) response.header("Retry-After", "60");
        if (error == ErrorCode.AI_ADVISOR_CAPACITY_EXCEEDED)
            response.header("Retry-After", ADVISOR_RETRY_AFTER_SECONDS);
        return response.body(apiResponseFactory
                .<Void>builder()
                .success(false)
                .code(error.getCode())
                .message(error.getMessage())
                .path(request.getRequestURI())
                .errors(exception.getFieldErrors().isEmpty() ? null : exception.getFieldErrors())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(apiResponseFactory.validationError(fieldErrors, request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        // Giữ field error do DTO từ chối field lạ, không đưa lỗi parse hoặc body thô ra ngoài.
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof AiAssistantException advisorException)
                return handleAiException(advisorException, request);
            if (cause instanceof GpaSimulationException simulationException)
                return handleSimulationException(simulationException, request);
        }
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(apiResponseFactory.error(ErrorCode.VALIDATION_ERROR, request.getRequestURI()));
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidDocument(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(apiResponseFactory.error(ErrorCode.INVALID_AI_DOCUMENT, request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflictingSource(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        ErrorCode error = ErrorCode.UNEXPECTED_ERROR;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintException) {
                String constraintName = constraintException.getConstraintName();
                if ("uk_knowledge_sources_file_hash".equals(constraintName))
                    error = ErrorCode.KNOWLEDGE_SOURCE_DUPLICATE;
                if ("uk_knowledge_sources_current_successor".equals(constraintName))
                    error = ErrorCode.KNOWLEDGE_SOURCE_STATE_INVALID;
            }
        }
        return ResponseEntity.status(error.getHttpStatus())
                .body(apiResponseFactory.error(error, request.getRequestURI()));
    }
}
