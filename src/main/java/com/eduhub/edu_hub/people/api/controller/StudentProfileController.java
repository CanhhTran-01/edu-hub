package com.eduhub.edu_hub.people.api.controller;

import com.eduhub.edu_hub.people.api.dto.request.UpdateMyStudentProfileRequest;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.application.StudentProfileService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
public class StudentProfileController {
    private final StudentProfileService profiles;

    @GetMapping
    public ApiResponse<StudentResponse> getMyProfile(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(profiles.get(authorization));
    }

    @PatchMapping
    public ApiResponse<StudentResponse> updateMyProfile(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody UpdateMyStudentProfileRequest request) {
        return ApiResponse.success(profiles.update(authorization, request));
    }
}
