package com.eduhub.edu_hub.curriculum.api.exception;

import com.eduhub.edu_hub.curriculum.api.controller.SubjectController;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.exception.PrerequisiteCycleException;
import com.eduhub.edu_hub.curriculum.application.exception.SubjectHasDependentsException;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = SubjectController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CurriculumExceptionHandler {
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
}
