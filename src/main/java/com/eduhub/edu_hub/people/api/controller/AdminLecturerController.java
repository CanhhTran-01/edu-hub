package com.eduhub.edu_hub.people.api.controller;

import com.eduhub.edu_hub.people.api.dto.request.CreateLecturerRequest;
import com.eduhub.edu_hub.people.api.dto.request.LecturerSearchRequest;
import com.eduhub.edu_hub.people.api.dto.request.UpdateLecturerProfileRequest;
import com.eduhub.edu_hub.people.api.dto.response.CreateLecturerResponse;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.service.LecturerAdminUpdateService;
import com.eduhub.edu_hub.people.application.service.LecturerApplicationService;
import com.eduhub.edu_hub.people.application.service.LecturerCreationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lecturers")
@RequiredArgsConstructor
public class AdminLecturerController {
    private final LecturerApplicationService lecturerApplicationService;
    private final LecturerCreationService lecturerCreationService;
    private final LecturerAdminUpdateService lecturerAdminUpdateService;

    @GetMapping
    public ApiResponse<PageResponse<LecturerResponse>> search(LecturerSearchRequest request) {
        PageResponse<LecturerResponse> lecturers = lecturerApplicationService.search(request);
        return ApiResponse.<PageResponse<LecturerResponse>>builder()
                .success(true)
                .message(lecturers.getContent().isEmpty() ? "Không tìm thấy kết quả" : null)
                .data(lecturers)
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<LecturerResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(lecturerApplicationService.findById(id));
    }

    @PostMapping
    public ApiResponse<CreateLecturerResponse> create(
            @Valid @RequestBody CreateLecturerRequest request, @AuthenticationPrincipal Jwt authenticatedJwt) {
        return ApiResponse.success(lecturerCreationService.createLecturer(authenticatedJwt, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<LecturerResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLecturerProfileRequest request,
            @AuthenticationPrincipal Jwt authenticatedJwt) {
        return ApiResponse.success(lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, id, request));
    }
}
