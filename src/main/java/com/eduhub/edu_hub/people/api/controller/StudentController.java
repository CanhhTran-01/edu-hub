package com.eduhub.edu_hub.people.api.controller;

import com.eduhub.edu_hub.people.api.dto.request.CreateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.request.StudentSearchRequest;
import com.eduhub.edu_hub.people.api.dto.request.UpdateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.people.api.dto.response.CreateStudentResponse;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.application.StudentApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentController {
    private final StudentApplicationService studentService;

    @PostMapping
    public ApiResponse<CreateStudentResponse> create(@Valid @RequestBody CreateStudentRequest request) {
        return ApiResponse.success(studentService.create(request));
    }

    @PostMapping("/{id}/account-notification/resend")
    public ApiResponse<AccountNotificationResponse> resendAccountNotification(@PathVariable Long id) {
        return ApiResponse.success(studentService.resendAccountNotification(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<StudentResponse>> search(
            StudentSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0 || size < 1) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }

        return ApiResponse.<PageResponse<StudentResponse>>builder()
                .success(true)
                .message("Không tìm thấy kết quả")
                .data(studentService.search(request, page, size))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(studentService.findById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudentResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateStudentRequest request) {
        return ApiResponse.success(studentService.update(id, request));
    }
}
