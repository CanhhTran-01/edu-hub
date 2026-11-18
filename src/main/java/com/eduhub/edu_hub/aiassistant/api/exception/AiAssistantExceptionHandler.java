package com.eduhub.edu_hub.aiassistant.api.exception;

import com.eduhub.edu_hub.aiassistant.api.controller.AiChatController;
import com.eduhub.edu_hub.aiassistant.api.controller.KnowledgeSourceController;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = {AiChatController.class, KnowledgeSourceController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AiAssistantExceptionHandler {
    private final ApiResponseFactory apiResponseFactory;

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
        var response = ResponseEntity.status(error.getHttpStatus());
        if (error == ErrorCode.AI_RATE_LIMIT_EXCEEDED) response.header("Retry-After", "60");
        return response.body(apiResponseFactory
                .<Void>builder()
                .success(false)
                .code(error.getCode())
                .message(error.getMessage())
                .path(request.getRequestURI())
                .errors(exception.getFieldErrors().isEmpty() ? null : exception.getFieldErrors())
                .build());
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
