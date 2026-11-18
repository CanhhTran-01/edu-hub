package com.eduhub.edu_hub.enrollment.api.controller;

import com.eduhub.edu_hub.enrollment.api.dto.request.ImportProcessScoresRequest;
import com.eduhub.edu_hub.enrollment.api.dto.request.UpdateProcessScoresRequest;
import com.eduhub.edu_hub.enrollment.api.dto.response.ProcessScoreRosterResponse;
import com.eduhub.edu_hub.enrollment.application.service.ProcessScoreService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/module-classes/{moduleClassId}/process-scores")
@RequiredArgsConstructor
public class ProcessScoreController {
    private final ApiResponseFactory apiResponseFactory;
    private final ProcessScoreService processScoreService;

    @GetMapping
    public ApiResponse<ProcessScoreRosterResponse> findScores(
            @AuthenticationPrincipal Jwt principal, @PathVariable Long moduleClassId) {
        return apiResponseFactory.success(processScoreService.findScores(principal, moduleClassId));
    }

    @PatchMapping
    public ApiResponse<ProcessScoreRosterResponse> updateScores(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long moduleClassId,
            @RequestBody UpdateProcessScoresRequest request) {
        return apiResponseFactory.success(processScoreService.updateScores(principal, moduleClassId, request));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @AuthenticationPrincipal Jwt principal, @PathVariable Long moduleClassId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=module-class-" + moduleClassId + "-process-scores.xlsx")
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(processScoreService.createTemplate(principal, moduleClassId));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProcessScoreRosterResponse> importScores(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable Long moduleClassId,
            @RequestPart("file") MultipartFile file) {
        return apiResponseFactory.success(
                processScoreService.importScores(principal, moduleClassId, new ImportProcessScoresRequest(file)));
    }
}
