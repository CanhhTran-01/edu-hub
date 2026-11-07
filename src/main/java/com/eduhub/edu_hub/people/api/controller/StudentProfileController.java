package com.eduhub.edu_hub.people.api.controller;

import com.eduhub.edu_hub.people.api.dto.request.UpdateMyStudentProfileRequest;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.application.StudentProfileService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
public class StudentProfileController {
    private final StudentProfileService profiles;

    @GetMapping
    public ApiResponse<StudentResponse> getMyProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(profiles.get(token(authorization)));
    }

    @PatchMapping
    public ApiResponse<StudentResponse> updateMyProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateMyStudentProfileRequest request) {
        return ApiResponse.success(profiles.update(token(authorization), request));
    }

    private String token(String authorization) {
        if (authorization == null
                || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED);
        }
        return authorization.substring(7);
    }
}
