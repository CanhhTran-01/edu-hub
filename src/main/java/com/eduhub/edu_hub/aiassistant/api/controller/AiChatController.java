package com.eduhub.edu_hub.aiassistant.api.controller;

import com.eduhub.edu_hub.aiassistant.api.dto.request.ChatRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatSessionDetailResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatSessionSummaryResponse;
import com.eduhub.edu_hub.aiassistant.application.service.AiChatService;
import com.eduhub.edu_hub.aiassistant.application.service.ChatHistoryService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Validated
public class AiChatController {
    private final AiChatService chatService;
    private final ChatHistoryService historyService;

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(chatService.chat(authenticatedJwt, request));
    }

    @GetMapping("/sessions")
    public ApiResponse<PageResponse<ChatSessionSummaryResponse>> sessions(
            @AuthenticationPrincipal Jwt authenticatedJwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(historyService.sessions(authenticatedJwt, page, size));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<ChatSessionDetailResponse> session(
            @AuthenticationPrincipal Jwt authenticatedJwt, @PathVariable String sessionId) {
        return ApiResponse.success(historyService.session(authenticatedJwt, sessionId));
    }
}
