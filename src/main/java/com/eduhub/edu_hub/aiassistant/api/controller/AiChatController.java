package com.eduhub.edu_hub.aiassistant.api.controller;

import com.eduhub.edu_hub.aiassistant.api.dto.request.AdvisorChatRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.AdvisorRequestStatusResponse;
import com.eduhub.edu_hub.aiassistant.application.service.AiAdvisorChatService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Validated
public class AiChatController {
    private static final long STREAM_COMPLETION_GRACE_MILLIS = 1000;
    private final ApiResponseFactory apiResponseFactory;
    private final AiAdvisorChatService advisorChatService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamAdvisorChat(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Valid @RequestBody AdvisorChatRequest request) {
        var execution = advisorChatService.prepareChat(authenticatedJwt, request);
        var emitter = new SseEmitter(execution.getRemainingMillis() + STREAM_COMPLETION_GRACE_MILLIS);
        execution.connect(
                (eventName, envelope) -> emitter.send(SseEmitter.event()
                        .name(eventName)
                        .id(Long.toString(envelope.sequence()))
                        .data(envelope)),
                emitter::complete);
        emitter.onTimeout(() -> execution.limit("CONNECTION_TIMEOUT"));
        emitter.onError(exception -> execution.disconnect());
        emitter.onCompletion(execution::disconnect);
        advisorChatService.startChat(execution);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @GetMapping("/chat-requests/{requestId}")
    public ResponseEntity<ApiResponse<AdvisorRequestStatusResponse>> findAdvisorRequest(
            @AuthenticationPrincipal Jwt authenticatedJwt, @PathVariable UUID requestId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(advisorChatService.findRequestStatus(authenticatedJwt, requestId)));
    }

    @PostMapping("/chat-requests/{requestId}/cancel")
    public ResponseEntity<ApiResponse<AdvisorRequestStatusResponse>> cancelAdvisorRequest(
            @AuthenticationPrincipal Jwt authenticatedJwt, @PathVariable UUID requestId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(apiResponseFactory.success(advisorChatService.cancelRequest(authenticatedJwt, requestId)));
    }
}
