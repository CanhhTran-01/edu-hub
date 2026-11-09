package com.eduhub.edu_hub.people.api.controller;

import com.eduhub.edu_hub.people.api.dto.request.CreateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.request.StudentSearchRequest;
import com.eduhub.edu_hub.people.api.dto.request.UpdateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.response.CreateStudentResponse;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.application.service.StudentApplicationService;
import com.eduhub.edu_hub.people.application.service.StudentProfileUpdateService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class AdminStudentController {
    private final StudentApplicationService studentApplicationService;
    private final StudentProfileUpdateService studentProfileUpdateService;

    @GetMapping
    public ApiResponse<PageResponse<StudentResponse>> search(StudentSearchRequest request) {
        PageResponse<StudentResponse> students = studentApplicationService.search(request);
        return ApiResponse.<PageResponse<StudentResponse>>builder()
                .success(true)
                .message(students.getContent().isEmpty() ? "Không tìm thấy kết quả" : null)
                .data(students)
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(studentApplicationService.findById(id));
    }

    @PostMapping
    public ApiResponse<CreateStudentResponse> create(@Valid @RequestBody CreateStudentRequest request) {
        return ApiResponse.success(studentApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStudentRequest request,
            @AuthenticationPrincipal Jwt authenticatedJwt) {

        // TODO(AUTHORIZATION): Giới hạn cho ACADEMIC_ADMIN, không cấp quyền cập nhật cho SYSTEM_ADMIN.
        //  Hiện chưa bật: STUDENT có thể gọi API này để sửa các trường ngoài whitelist của PATCH /students/me.

        return ApiResponse.success(studentProfileUpdateService.updateStudentProfile(authenticatedJwt, id, request));
    }
}
