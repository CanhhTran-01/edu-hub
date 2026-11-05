package com.eduhub.edu_hub.enrollment.application.facade.dto;

import java.util.List;

public record LearningHistoryResponse(boolean historyComplete, List<LearningAttemptResponse> attempts) {}
