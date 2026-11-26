package com.eduhub.edu_hub.aiassistant.api.dto.response;

import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.aiassistant.domain.enums.AdvisorRequestState;
import java.time.LocalDateTime;
import java.util.UUID;

/** Trạng thái trong thời hạn lưu runtime; chỉ chủ sở hữu được xem kết quả. */
public record AdvisorRequestStatusResponse(
        UUID requestId,
        UUID clientRequestId,
        String sessionId,
        AdvisorRequestState state,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long lastSequence,
        int modelCalls,
        int toolCalls,
        int reservedTokens,
        String stopReason,
        Integer errorCode,
        ChatResponse response,
        AdvisorAcademicProfileResponse academicProfile) {}
