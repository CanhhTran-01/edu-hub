package com.eduhub.edu_hub.admission.api.controller;

import com.eduhub.edu_hub.admission.api.dto.request.PrepareAdmissionClassesRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdministrativeClassResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
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
    private final AdmissionClassPreparationService classPreparationService;

    // TODO: phía FE cần làm dropdown hiển thị list các kịch bản đã tạo do BE trả về (bước 5)

    // 5. Lôi các kịch bản đã tạo lên
    @GetMapping
    public ApiResponse<List<AdministrativeClassResponse>> listAdministrativeClasses(
            @RequestParam Long majorId, @RequestParam Long cohortId) {
        return ApiResponse.success(classPreparationService.listAdministrativeClasses(majorId, cohortId));
    }

    // 6. Chọn kịch bản ưng ý (nên theo đề xuất) và chuẩn bị lớp
    @PostMapping("/from-admission-plan")
    public ApiResponse<AdmissionPlanResponse> prepareClasses(
            @Valid @RequestBody PrepareAdmissionClassesRequest request) {
        return ApiResponse.success(classPreparationService.prepareClasses(request));
    }
}
