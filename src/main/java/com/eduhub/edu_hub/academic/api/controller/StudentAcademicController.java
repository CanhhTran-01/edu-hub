package com.eduhub.edu_hub.academic.api.controller;

import com.eduhub.edu_hub.academic.api.dto.response.*;
import com.eduhub.edu_hub.academic.application.service.StudentAcademicQueryService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/students/me/academic")
public class StudentAcademicController {
    private final ApiResponseFactory apiResponseFactory;
    private final StudentAcademicQueryService studentAcademicQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<StudentAcademicOverviewResponse>> findMyAcademicOverview(
            @AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(studentAcademicQueryService.findMyAcademicOverview(principal)));
    }

    @GetMapping("/transcript")
    public ResponseEntity<ApiResponse<StudentTranscriptResponse>> findMyTranscript(
            @AuthenticationPrincipal Jwt principal, @RequestParam(required = false) Long semesterId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(studentAcademicQueryService.findMyTranscript(principal, semesterId)));
    }

    @GetMapping("/registration")
    public ResponseEntity<ApiResponse<StudentSemesterRegistrationResponse>> findMySemsterRegistrations(
            @AuthenticationPrincipal Jwt principal, @RequestParam Long semesterId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(
                        studentAcademicQueryService.findMySemsterRegistrations(principal, semesterId)));
    }
}
