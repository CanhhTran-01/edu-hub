package com.eduhub.edu_hub.aiassistant.api.controller;

import com.eduhub.edu_hub.aiassistant.api.dto.response.DocumentUploadResponse;
import com.eduhub.edu_hub.aiassistant.application.service.DocumentIngestionService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai/documents")
@RequiredArgsConstructor
public class AiDocumentController {
    private final DocumentIngestionService ingestionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentUploadResponse> upload(
            @AuthenticationPrincipal Jwt authenticatedJwt, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(ingestionService.upload(authenticatedJwt, file));
    }
}
