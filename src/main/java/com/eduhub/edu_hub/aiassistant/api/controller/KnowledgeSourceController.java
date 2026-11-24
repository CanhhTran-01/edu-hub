package com.eduhub.edu_hub.aiassistant.api.controller;

import com.eduhub.edu_hub.aiassistant.api.dto.request.*;
import com.eduhub.edu_hub.aiassistant.api.dto.response.KnowledgeSourceDownloadResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.response.KnowledgeSourceResponse;
import com.eduhub.edu_hub.aiassistant.application.service.KnowledgeSourceService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/documents")
@RequiredArgsConstructor
@Validated
public class KnowledgeSourceController {
    private final ApiResponseFactory apiResponseFactory;
    private final KnowledgeSourceService sourceService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<KnowledgeSourceResponse> upload(
            @AuthenticationPrincipal Jwt authenticatedJwt,
            @Valid @ModelAttribute UploadKnowledgeSourceRequest request) {
        return apiResponseFactory.success(sourceService.upload(authenticatedJwt, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<KnowledgeSourceResponse>> findSources(
            @AuthenticationPrincipal Jwt authenticatedJwt,
            @Valid @ModelAttribute SearchKnowledgeSourcesRequest request) {
        return apiResponseFactory.success(sourceService.findSources(authenticatedJwt, request));
    }

    @GetMapping("/{sourceId}")
    public ApiResponse<KnowledgeSourceResponse> findSource(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Positive @PathVariable Long sourceId) {
        return apiResponseFactory.success(sourceService.findSource(authenticatedJwt, sourceId));
    }

    @PostMapping("/{sourceId}/activate")
    public ApiResponse<KnowledgeSourceResponse> activateSource(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Positive @PathVariable Long sourceId) {
        return apiResponseFactory.success(sourceService.activateSource(authenticatedJwt, sourceId));
    }

    @GetMapping("/{sourceId}/download")
    public ResponseEntity<byte[]> downloadSourceFile(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Positive @PathVariable Long sourceId) {
        KnowledgeSourceDownloadResponse download = sourceService.downloadSourceFile(authenticatedJwt, sourceId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.fileBytes().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(download.fileBytes());
    }

    @PostMapping("/{sourceId}/deactivate")
    public ApiResponse<KnowledgeSourceResponse> deactivateSource(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Positive @PathVariable Long sourceId) {
        return apiResponseFactory.success(sourceService.deactivateSource(authenticatedJwt, sourceId));
    }

    @PostMapping("/{sourceId}/reprocess")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<KnowledgeSourceResponse> reprocessSource(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Positive @PathVariable Long sourceId) {
        return apiResponseFactory.success(sourceService.reprocessSource(authenticatedJwt, sourceId));
    }

    @DeleteMapping("/{sourceId}")
    public ApiResponse<Void> deleteSource(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Positive @PathVariable Long sourceId) {
        sourceService.deleteSource(authenticatedJwt, sourceId);
        return apiResponseFactory.success(null);
    }
}
