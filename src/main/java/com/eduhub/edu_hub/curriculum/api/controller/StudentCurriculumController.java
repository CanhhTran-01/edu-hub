package com.eduhub.edu_hub.curriculum.api.controller;

import com.eduhub.edu_hub.curriculum.api.dto.response.StudentCurriculumResponse;
import com.eduhub.edu_hub.curriculum.application.service.StudentCurriculumQueryService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/students/me/curriculum")
public class StudentCurriculumController {
    private final ApiResponseFactory apiResponseFactory;
    private final StudentCurriculumQueryService studentCurriculumQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<StudentCurriculumResponse>> findMyCurriculum(
            @AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(studentCurriculumQueryService.findMyCurriculum(principal)));
    }
}
