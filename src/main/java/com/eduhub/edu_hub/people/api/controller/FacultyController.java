package com.eduhub.edu_hub.people.api.controller;

import com.eduhub.edu_hub.people.api.dto.response.FacultyResponse;
import com.eduhub.edu_hub.people.application.service.FacultyQueryService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/faculties")
@RequiredArgsConstructor
public class FacultyController {
    private final ApiResponseFactory apiResponseFactory;
    private final FacultyQueryService facultyQueryService;

    @GetMapping
    public ApiResponse<List<FacultyResponse>> findFaculties() {
        return apiResponseFactory.success(facultyQueryService.findFaculties());
    }
}
