package com.eduhub.edu_hub.people.api;

import com.eduhub.edu_hub.people.api.dto.StudentResponse;
import com.eduhub.edu_hub.people.api.dto.UpdateMyStudentProfileRequest;
import com.eduhub.edu_hub.people.application.MyStudentProfileService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
public class MyStudentProfileController {
    private final MyStudentProfileService profiles;

    @GetMapping
    public ApiResponse<StudentResponse> get(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(profiles.get(token(authorization)));
    }

    @PatchMapping
    public ApiResponse<StudentResponse> update(@RequestHeader("Authorization") String authorization,
            @Valid @RequestBody UpdateMyStudentProfileRequest request) {
        return ApiResponse.success(profiles.update(token(authorization), request));
    }

    private String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7)
            throw new BaseException(ErrorCode.UNAUTHENTICATED);
        return authorization.substring(7);
    }
}
