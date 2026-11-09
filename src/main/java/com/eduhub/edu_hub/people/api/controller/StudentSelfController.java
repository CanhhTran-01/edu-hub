package com.eduhub.edu_hub.people.api.controller;

import com.eduhub.edu_hub.people.api.dto.request.UpdateMyProfileRequest;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.application.service.StudentSelfService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StudentSelfController {
    private final StudentSelfService studentSelfService;

    // TODO(AUTHORIZATION): Khi triển khai phân quyền, chỉ STUDENT được xem/sửa hồ sơ của chính mình tại /me.
    //  SYSTEM_ADMIN không có ngoại lệ cập nhật hồ sơ sinh viên. Hiện chỉ xác thực, chưa chặn theo role.

    @GetMapping("/students/me")
    public ApiResponse<StudentResponse> getMyProfile(@AuthenticationPrincipal Jwt authenticatedJwt) {
        return ApiResponse.success(studentSelfService.getMyProfile(authenticatedJwt));
    }

    @PatchMapping("/students/me")
    public ApiResponse<StudentResponse> updateMyProfile(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Valid @RequestBody UpdateMyProfileRequest request) {
        return ApiResponse.success(studentSelfService.updateMyProfile(authenticatedJwt, request));
    }
}
