package com.eduhub.edu_hub.academic.application.facade.impl;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest;
import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest;
import com.eduhub.edu_hub.academic.api.dto.response.GpaGoalResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationPreviewResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationSnapshotResponse;
import com.eduhub.edu_hub.academic.application.facade.AcademicFacade;
import com.eduhub.edu_hub.academic.application.service.StudentGpaGoalService;
import com.eduhub.edu_hub.academic.application.service.StudentGpaSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AcademicGpaSimulationFacadeImpl implements AcademicFacade.GpaSimulation {
    private final StudentGpaSimulationService simulationService;
    private final StudentGpaGoalService goalService;

    @Override
    public GpaSimulationSnapshotResponse findMySnapshot(Jwt principal) {
        return simulationService.findMySnapshot(principal);
    }

    @Override
    public GpaSimulationPreviewResponse previewMyGpa(Jwt principal, GpaSimulationRequest request) {
        return simulationService.previewMyGpa(principal, request);
    }

    @Override
    public GpaGoalResponse solveMyGpaGoal(Jwt principal, GpaGoalRequest request) {
        return goalService.solveMyGpaGoal(principal, request);
    }
}
