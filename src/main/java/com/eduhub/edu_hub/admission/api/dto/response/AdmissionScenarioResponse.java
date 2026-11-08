package com.eduhub.edu_hub.admission.api.dto.response;

import java.util.List;
import lombok.Builder;

@Builder
public record AdmissionScenarioResponse(
        int classCount,
        List<Integer> targetSizes,
        String level,
        int reserveUsed,
        boolean belowMinimum,
        boolean insufficientRooms,
        long suitableRooms,
        int requiredRoomCapacity,
        boolean recommended,
        List<String> warnings) {}
