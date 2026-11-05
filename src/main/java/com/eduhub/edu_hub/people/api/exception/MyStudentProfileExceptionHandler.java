package com.eduhub.edu_hub.people.api.exception;

import com.eduhub.edu_hub.people.api.controller.MyStudentProfileController;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MyStudentProfileController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MyStudentProfileExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {

        String message = "JSON không hợp lệ; chỉ được cập nhật targetGpa và phone";
        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof IllegalArgumentException
                    && cause.getMessage() != null
                    && cause.getMessage().startsWith("Trường không được phép cập nhật")) {

                message = cause.getMessage();
                break;
            }
            cause = cause.getCause();
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.validationError(
                        List.of(new FieldErrorDetail("request", message)), request.getRequestURI()));
    }
}
