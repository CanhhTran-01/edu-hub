package com.eduhub.edu_hub.people.api.controller;

import com.eduhub.edu_hub.people.api.dto.request.CreateCohortRequest;
import com.eduhub.edu_hub.people.api.dto.response.CohortResponse;
import com.eduhub.edu_hub.people.application.service.CohortApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cohorts")
@RequiredArgsConstructor
public class CohortController {
    private final ApiResponseFactory apiResponseFactory;
    private final CohortApplicationService cohortApplicationService;

    @GetMapping
    public ApiResponse<List<CohortResponse>> findCohorts() {
        return apiResponseFactory.success(cohortApplicationService.findCohorts());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CohortResponse>> createCohort(
            @AuthenticationPrincipal Jwt principal, @Valid @RequestBody CreateCohortRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiResponseFactory.success(cohortApplicationService.createCohort(principal, request)));
    }
}
