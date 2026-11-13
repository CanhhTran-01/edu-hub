package com.eduhub.edu_hub.admission.api.controller;

import com.eduhub.edu_hub.admission.api.dto.request.AdjustAdmissionAssignmentsRequest;
import com.eduhub.edu_hub.admission.api.dto.request.ConfirmAdmissionEnrollmentRequest;
import com.eduhub.edu_hub.admission.api.dto.request.CreateAdmissionPlanFromFileRequest;
import com.eduhub.edu_hub.admission.api.dto.request.CreateAdmissionPlanRequest;
import com.eduhub.edu_hub.admission.api.dto.request.ImportAdmissionApplicantsRequest;
import com.eduhub.edu_hub.admission.api.dto.request.PreviewAdmissionAllocationRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionCapacityResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionConfirmationResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.service.AdmissionAllocationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionConfirmationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionImportService;
import com.eduhub.edu_hub.admission.application.service.AdmissionNotificationQueryService;
import com.eduhub.edu_hub.admission.application.service.AdmissionPlanningService;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationResponse;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/student-admissions")
@RequiredArgsConstructor
public class StudentAdmissionController {
    private final AdmissionPlanningService planningService;
    private final AdmissionImportService importService;
    private final AdmissionAllocationService allocationService;
    private final AdmissionConfirmationService confirmationService;
    private final AdmissionNotificationQueryService notificationQueryService;

    // TODO: phần lấy cấu hình mặc định sẽ được làm ngầm sau khi tích hợp SystemParamConfig

    // 1. Lấy cấu hình mặc định: sĩ số tối đa, sĩ số tối thiểu, suất dự bị
    @GetMapping("/configuration")
    public ApiResponse<AdmissionCapacityResponse> getDefaultCapacityConfiguration() {
        return ApiResponse.success(planningService.getDefaultCapacityConfiguration());
    }

    // 2. Chọn ngành và khóa trước khi tải file Excel mẫu tương ứng
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadImportTemplate(@RequestParam Long majorId, @RequestParam Long cohortId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=admission-template.xlsx")
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(planningService.generateImportTemplate(majorId, cohortId));
    }

    // 3a. Nhánh A: Lập phương án nhập học từ số lượng sinh viên dự kiến
    @PostMapping("/plans")
    public ApiResponse<AdmissionPlanResponse> createPlan(@Valid @RequestBody CreateAdmissionPlanRequest request) {
        return ApiResponse.success(planningService.createPlan(request));
    }

    // 3b. Nhánh B: Lập phương án nhập học từ danh sách thực tế được import
    @PostMapping(value = "/plans/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdmissionPlanResponse> createPlanFromFile(
            @RequestParam Long majorId,
            @RequestParam Long cohortId,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) Integer maxCapacity,
            @RequestParam(required = false) Integer reserveCapacity,
            @RequestParam MultipartFile file) {
        return ApiResponse.success(planningService.createPlanFromFile(new CreateAdmissionPlanFromFileRequest(
                majorId, cohortId, minCapacity, maxCapacity, reserveCapacity, file)));
    }

    // 4. Xem lại kịch bản
    @GetMapping("/plans/{id}")
    public ApiResponse<AdmissionPlanResponse> getPlan(@PathVariable("id") Long planId) {
        return ApiResponse.success(planningService.getPlan(planId));
    }

    // 7. Nhập danh sách vào plan
    @PostMapping(value = "/plans/{id}/applicants", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdmissionPlanResponse> importApplicants(
            @PathVariable("id") Long planId, @RequestParam long expectedRevision, @RequestParam MultipartFile file) {
        return ApiResponse.success(
                importService.importApplicants(planId, new ImportAdmissionApplicantsRequest(expectedRevision, file)));
    }

    // 8. Chạy phân bổ học sinh vào các lớp theo plan và xem trước kết quả phân bổ
    @PostMapping("/plans/{id}/allocation/preview")
    public ApiResponse<AdmissionPlanResponse> previewAllocation(
            @PathVariable("id") Long planId, @Valid @RequestBody PreviewAdmissionAllocationRequest request) {
        return ApiResponse.success(allocationService.previewAllocation(planId, request));
    }

    // 9. Điều chỉnh kết quả phân bổ thủ công
    @PatchMapping("/plans/{id}/allocation")
    public ApiResponse<AdmissionPlanResponse> adjustAssignments(
            @PathVariable("id") Long planId, @Valid @RequestBody AdjustAdmissionAssignmentsRequest request) {
        return ApiResponse.success(allocationService.adjustAssignments(planId, request));
    }

    // 10. Chốt kết quả phân bổ
    @PostMapping("/plans/{id}/confirm")
    public ApiResponse<AdmissionConfirmationResponse> confirmEnrollment(
            @PathVariable("id") Long planId, @Valid @RequestBody ConfirmAdmissionEnrollmentRequest request) {
        return ApiResponse.success(confirmationService.confirmEnrollment(planId, request));
    }

    // 11. xem kết quả gửi thông báo sau khi đã chốt kết quả phân bổ
    @GetMapping("/plans/{id}/notifications")
    public ApiResponse<List<AdmissionNotificationResponse>> listPlanNotifications(@PathVariable("id") Long planId) {
        planningService.getPlan(planId);
        return ApiResponse.success(notificationQueryService.listPlanNotifications(planId));
    }
}
