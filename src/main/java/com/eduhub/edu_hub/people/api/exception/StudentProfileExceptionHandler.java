package com.eduhub.edu_hub.people.api.exception;

import com.eduhub.edu_hub.people.api.controller.AdminStudentController;
import com.eduhub.edu_hub.people.api.controller.StudentSelfController;
import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.MismatchedInputException;

@RestControllerAdvice(assignableTypes = {StudentSelfController.class, AdminStudentController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StudentProfileExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleProfileDataConflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintException
                    && "uk_students_active_citizen_id".equals(constraintException.getConstraintName())) {
                // Kiểm tra trước bằng repository chưa đủ bảo vệ ghi cạnh tranh; giữ lỗi theo field khi unique chặn.
                return handleStudentProfileException(
                        new StudentProfileException(
                                ErrorCode.DUPLICATE_STUDENT_CITIZEN_ID, "citizenId", "CCCD sinh viên đã tồn tại"),
                        request);
            }
            cause = cause.getCause();
        }
        // Không suy mọi lỗi toàn vẹn là CCCD trùng và không đưa SQL/dữ liệu nhạy cảm ra response.
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(ErrorCode.UNEXPECTED_ERROR, request.getRequestURI()));
    }

    @ExceptionHandler(StudentProfileException.class)
    public ResponseEntity<ApiResponse<Void>> handleStudentProfileException(
            StudentProfileException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .errors(
                                exception.getField() == null
                                        ? null
                                        : List.of(new FieldErrorDetail(
                                                exception.getField(), exception.getFieldMessage())))
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {

        String field = "request";
        String message = "JSON không hợp lệ";
        Throwable cause = exception;

        while (cause != null) {

            // Jackson bọc lỗi từ setter; đọc đúng type thay vì dựa vào nội dung message.
            if (cause instanceof StudentProfileException profileException) {
                return handleStudentProfileException(profileException, request);
            }
            if (cause instanceof MismatchedInputException inputException
                    && !inputException.getPath().isEmpty()) {
                String inputField = inputException.getPath().getFirst().getPropertyName();
                if (inputField != null) {
                    field = inputField;
                    message = "Giá trị trường không đúng định dạng";
                }
            }
            cause = cause.getCause();
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.validationError(
                        List.of(new FieldErrorDetail(field, message)), request.getRequestURI()));
    }
}
