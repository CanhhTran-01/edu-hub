package com.eduhub.edu_hub.scheduling.api.dto.response;

import com.eduhub.edu_hub.scheduling.domain.enums.TimetablePlanState;
import java.time.LocalDateTime;
import java.util.List;

public record TimetablePlanResponse(
        Long id,
        Long semesterId,
        long revision,
        TimetablePlanState state,
        Long hardPenalty,
        Long softPenalty,
        List<String> violations,
        TimetableAllocationResponse allocation,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canApply) {}
