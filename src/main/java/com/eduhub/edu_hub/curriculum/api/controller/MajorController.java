package com.eduhub.edu_hub.curriculum.api.controller;

import com.eduhub.edu_hub.curriculum.api.dto.response.MajorResponse;
import com.eduhub.edu_hub.curriculum.application.service.MajorQueryService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/majors")
@RequiredArgsConstructor
public class MajorController {
    private final ApiResponseFactory apiResponseFactory;
    private final MajorQueryService majorQueryService;

    @GetMapping
    public ApiResponse<List<MajorResponse>> findMajors() {
        return apiResponseFactory.success(majorQueryService.findMajors());
    }
}
