package com.eduhub.edu_hub.enrollment.api.controller;

import com.eduhub.edu_hub.enrollment.api.dto.request.RegisterModuleClassRequest;
import com.eduhub.edu_hub.enrollment.api.dto.response.*;
import com.eduhub.edu_hub.enrollment.application.service.EnrollmentApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
public class EnrollmentController {
    private final ApiResponseFactory apiResponseFactory;
    private final EnrollmentApplicationService enrollmentService;

    @GetMapping("/semesters/{semesterId}/registration")
    public ApiResponse<EnrollmentRegistrationOptionsResponse> findOptions(
            @AuthenticationPrincipal Jwt principal, @PathVariable Long semesterId) {
        return apiResponseFactory.success(enrollmentService.findOptions(principal, semesterId));
    }

    @PostMapping("/enrollments")
    public ApiResponse<EnrollmentRegistrationResponse> register(
            @AuthenticationPrincipal Jwt principal, @Valid @RequestBody RegisterModuleClassRequest request) {
        return apiResponseFactory.success(enrollmentService.register(principal, request));
    }

    @PostMapping("/enrollments/{enrollmentId}/drop")
    public ApiResponse<EnrollmentRegistrationResponse> drop(
            @AuthenticationPrincipal Jwt principal, @PathVariable Long enrollmentId) {
        return apiResponseFactory.success(enrollmentService.drop(principal, enrollmentId));
    }
}
