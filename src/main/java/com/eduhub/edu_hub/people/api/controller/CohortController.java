package com.eduhub.edu_hub.people.api.controller;

import com.eduhub.edu_hub.people.api.dto.response.CohortResponse;
import com.eduhub.edu_hub.people.application.service.CohortQueryService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cohorts")
@RequiredArgsConstructor
public class CohortController {
    private final ApiResponseFactory apiResponseFactory;
    private final CohortQueryService cohortQueryService;

    @GetMapping
    public ApiResponse<List<CohortResponse>> findCohorts() {
        return apiResponseFactory.success(cohortQueryService.findCohorts());
    }
}
