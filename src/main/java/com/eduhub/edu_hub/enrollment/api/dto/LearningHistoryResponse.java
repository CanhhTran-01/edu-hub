package com.eduhub.edu_hub.enrollment.api.dto;

import java.util.List;

public record LearningHistoryResponse(boolean historyComplete, List<LearningAttemptResponse> attempts) {}
