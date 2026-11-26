package com.eduhub.edu_hub.aiassistant.api.controller;

import com.eduhub.edu_hub.aiassistant.api.dto.request.SearchChatSessionsRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatSessionDetailResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatSessionSummaryResponse;
import com.eduhub.edu_hub.aiassistant.application.service.ChatHistoryService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Đọc danh sách và lịch sử hội thoại; việc gửi tin nhắn do AiChatController tiếp nhận. */
@RestController
@RequestMapping("/api/v1/ai/sessions")
@RequiredArgsConstructor
@Validated
public class AiChatSessionController {
    private final ApiResponseFactory apiResponseFactory;
    private final ChatHistoryService historyService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ChatSessionSummaryResponse>>> getAllSessions(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Valid @ModelAttribute SearchChatSessionsRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(historyService.getAllSessions(authenticatedJwt, request)));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<ChatSessionDetailResponse>> getSessionById(
            @AuthenticationPrincipal Jwt authenticatedJwt, @PathVariable String sessionId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(historyService.getSessionById(authenticatedJwt, sessionId)));
    }
}
