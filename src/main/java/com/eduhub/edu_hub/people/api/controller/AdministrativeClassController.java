package com.eduhub.edu_hub.people.api.controller;

import com.eduhub.edu_hub.people.api.dto.request.AdmissionRequests.CreateClassesRequest;
import com.eduhub.edu_hub.people.api.dto.response.AdministrativeClassResponse;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionResponses.PlanResponse;
import com.eduhub.edu_hub.people.application.admission.AdministrativeClassService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/administrative-classes")
@RequiredArgsConstructor
public class AdministrativeClassController {
    private final AdministrativeClassService classes;

    // Xem các lớp hiện có
    @GetMapping
    public ApiResponse<List<AdministrativeClassResponse>> list(
            @RequestParam Long majorId, @RequestParam Long cohortId) {
        return ApiResponse.success(classes.list(majorId, cohortId));
    }

    // Chọn kịch bản và chuẩn bị lớp
    @PostMapping("/from-admission-plan")
    public ApiResponse<PlanResponse> create(@Valid @RequestBody CreateClassesRequest request) {
        return ApiResponse.success(classes.createFromPlan(request));
    }
}
