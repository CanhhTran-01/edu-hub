package com.eduhub.edu_hub.aiassistant.api.dto.response;

import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationPreviewResponse;

/** Nhận xét tạm cho robot và bộ điểm đã được nhận xét; không mang session/message ID. */
public record GpaSimulationCommentResponse(GpaSimulationPreviewResponse preview, String comment) {}
