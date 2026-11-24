package com.eduhub.edu_hub.curriculum.api.controller;

import com.eduhub.edu_hub.curriculum.api.dto.response.TrainingTypeResponse;
import com.eduhub.edu_hub.curriculum.application.service.TrainingTypeQueryService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/training-types")
@RequiredArgsConstructor
public class TrainingTypeController {
    private final ApiResponseFactory apiResponseFactory;
    private final TrainingTypeQueryService trainingTypeQueryService;

    @GetMapping
    public ApiResponse<List<TrainingTypeResponse>> findTrainingTypes() {
        return apiResponseFactory.success(trainingTypeQueryService.findTrainingTypes());
    }
}
