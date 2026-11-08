package com.eduhub.edu_hub.admission.api.controller;

import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.PrepareClassesRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdministrativeClassResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionResponses.PlanResponse;
import com.eduhub.edu_hub.admission.api.mapper.AdmissionApiMapper;
import com.eduhub.edu_hub.admission.application.service.AdmissionClassPreparationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/administrative-classes")
@RequiredArgsConstructor
public class AdministrativeClassController {
    private final AdmissionApiMapper apiMapper;
    private final AdmissionClassPreparationService classPreparationService;

    // Xem các lớp hiện có
    @GetMapping
    public ApiResponse<List<AdministrativeClassResponse>> listAdministrativeClasses(
            @RequestParam Long majorId, @RequestParam Long cohortId) {
        return ApiResponse.success(classPreparationService.listAdministrativeClasses(majorId, cohortId).stream()
                .map(apiMapper::toResponse)
                .toList());
    }

    // Chọn kịch bản và chuẩn bị lớp
    @PostMapping("/from-admission-plan")
    public ApiResponse<PlanResponse> prepareClasses(@Valid @RequestBody PrepareClassesRequest request) {
        return ApiResponse.success(
                apiMapper.toResponse(classPreparationService.prepareClasses(apiMapper.toCommand(request))));
    }
}
