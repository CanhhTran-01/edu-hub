package com.eduhub.edu_hub.shared.common;

import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private Integer code; // null khi success — chỉ có giá trị khi lỗi
    private String message;
    private T data; // null khi lỗi
    private List<FieldErrorDetail> errors; // null khi success — lỗi validation
    private String path; // null khi success — đường dẫn của request

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> successWithMessage(String message) {
        return ApiResponse.<T>builder().success(true).message(message).build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .path(path)
                .build();
    }

    public static <T> ApiResponse<T> validationError(List<FieldErrorDetail> errors, String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .message(ErrorCode.VALIDATION_ERROR.getMessage())
                .errors(errors)
                .path(path)
                .build();
    }
}
