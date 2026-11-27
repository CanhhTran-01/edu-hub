package com.eduhub.edu_hub.aiassistant.api.controller;

import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationPreviewResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationSnapshotResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.response.GpaSimulationCommentResponse;
import com.eduhub.edu_hub.aiassistant.application.service.AiGpaSimulationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** API JSON độc lập hội thoại, không dùng sessionId hoặc sinh message cho popup. */
@RestController
@RequestMapping("/api/v1/ai/what-if")
@RequiredArgsConstructor
public class AiGpaSimulationController {
    private final AiGpaSimulationService simulationService;
    private final ApiResponseFactory apiResponseFactory;

    @GetMapping("/snapshot")
    public ResponseEntity<ApiResponse<GpaSimulationSnapshotResponse>> findMySnapshot(
            @AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(simulationService.findMySnapshot(principal)));
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<GpaSimulationPreviewResponse>> previewMyGpa(
            @AuthenticationPrincipal Jwt principal, @RequestBody GpaSimulationRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(simulationService.previewMyGpa(principal, request)));
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<GpaSimulationCommentResponse>> analyzeMySimulation(
            @AuthenticationPrincipal Jwt principal, @RequestBody GpaSimulationRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(simulationService.analyzeMySimulation(principal, request)));
    }
}
