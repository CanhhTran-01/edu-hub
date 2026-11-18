package com.eduhub.edu_hub.shared.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Dữ liệu response; controller và handler dùng ApiResponseFactory để gắn timestamp từ Clock chung. */
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

    private LocalDateTime timestamp;
}
