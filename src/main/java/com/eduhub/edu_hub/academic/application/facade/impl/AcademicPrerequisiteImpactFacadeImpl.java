package com.eduhub.edu_hub.academic.application.facade.impl;

import com.eduhub.edu_hub.academic.api.dto.request.PrerequisiteImpactRequest;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse;
import com.eduhub.edu_hub.academic.application.facade.AcademicFacade;
import com.eduhub.edu_hub.academic.application.service.StudentPrerequisiteImpactService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AcademicPrerequisiteImpactFacadeImpl implements AcademicFacade.Prerequisites {
    private final StudentPrerequisiteImpactService prerequisiteService;

    @Override
    public PrerequisiteImpactResponse analyzeMyPrerequisites(Jwt principal, PrerequisiteImpactRequest request) {
        return prerequisiteService.analyzeMyPrerequisites(principal, request);
    }
}
