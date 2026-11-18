package com.eduhub.edu_hub.scheduling.api.dto.response;

import java.time.LocalDate;
import java.util.List;

public record TimetableResponse(
        Long semesterId,
        Long appliedPlanId,
        LocalDate phaseOneStart,
        LocalDate phaseOneEnd,
        LocalDate phaseTwoStart,
        LocalDate phaseTwoEnd,
        List<TimetableAllocationResponse.SubjectPhaseResponse> subjects,
        List<ModuleClassResponse> moduleClasses) {}
