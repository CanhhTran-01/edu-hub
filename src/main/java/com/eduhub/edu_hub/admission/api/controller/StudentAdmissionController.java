package com.eduhub.edu_hub.admission.api.controller;

import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.AdjustAssignmentsRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.ConfirmEnrollmentRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.CreatePlanRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.PreviewAllocationRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionResponses.Capacity;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionResponses.ConfirmationResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionResponses.NotificationResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionResponses.PlanResponse;
import com.eduhub.edu_hub.admission.api.mapper.AdmissionApiMapper;
import com.eduhub.edu_hub.admission.application.service.AdmissionAllocationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionConfirmationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionImportService;
import com.eduhub.edu_hub.admission.application.service.AdmissionNotificationQueryService;
import com.eduhub.edu_hub.admission.application.service.AdmissionPlanningService;
import com.eduhub.edu_hub.admission.infrastructure.config.AdmissionSettings;
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
    private final AdmissionApiMapper apiMapper;
    private final AdmissionPlanningService planningService;
    private final AdmissionImportService importService;
    private final AdmissionAllocationService allocationService;
    private final AdmissionConfirmationService confirmationService;
    private final AdmissionNotificationQueryService notificationQueryService;
    private final AdmissionSettings admissionSettings;

    // 1. Lấy cấu hình mặc định: sĩ số tối đa, sĩ số tối thiểu, suất dự bị
    @GetMapping("/configuration")
    public ApiResponse<Capacity> getDefaultCapacityConfiguration() {
        return ApiResponse.success(new Capacity(
                admissionSettings.getMinCapacity(),
                admissionSettings.getMaxCapacity(),
                admissionSettings.getReserveCapacity()));
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

    // 3. Lập phương án từ số sinh viên dự kiến
    // Dùng khi Admin đã biết số lượng dự kiến nhưng chưa có DS chi tiết. Chưa tạo lớp, sinh viên, tài khoản
    @PostMapping("/plans")
    public ApiResponse<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        return ApiResponse.success(apiMapper.toResponse(planningService.createPlan(apiMapper.toCommand(request))));
    }

    // 4. Lập phương án mới từ file tuyển sinh
    @PostMapping(value = "/plans/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PlanResponse> createPlanFromFile(
            @RequestParam Long majorId,
            @RequestParam Long cohortId,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) Integer maxCapacity,
            @RequestParam(required = false) Integer reserveCapacity,
            @RequestParam MultipartFile file) {
        return ApiResponse.success(apiMapper.toResponse(planningService.createPlanFromFile(
                apiMapper.toCommand(
                        new CreatePlanRequest(majorId, cohortId, 0, minCapacity, maxCapacity, reserveCapacity)),
                file)));
    }

    // 5. Xem lại plan
    @GetMapping("/plans/{id}")
    public ApiResponse<PlanResponse> getPlan(@PathVariable("id") Long planId) {
        return ApiResponse.success(apiMapper.toResponse(planningService.getPlan(planId)));
    }

    // 6. Nhập danh sách vào plan đã có
    // sử dụng plan hiện có, không tạo mới, hồ sơ đã nhập học thành công được giữ nguyên khi xử lý phần nhập lại
    @PostMapping(value = "/plans/{id}/applicants", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PlanResponse> importApplicants(
            @PathVariable("id") Long planId, @RequestParam long expectedRevision, @RequestParam MultipartFile file) {
        return ApiResponse.success(
                apiMapper.toResponse(importService.importApplicants(planId, expectedRevision, file)));
    }

    // 7. Chạy phân bổ và tạo bản xem trước
    @PostMapping("/plans/{id}/allocation/preview")
    public ApiResponse<PlanResponse> previewAllocation(
            @PathVariable("id") Long planId, @Valid @RequestBody PreviewAllocationRequest request) {
        return ApiResponse.success(
                apiMapper.toResponse(allocationService.previewAllocation(planId, apiMapper.toCommand(request))));
    }

    // 8. Điều chỉnh phân bổ thủ công
    @PatchMapping("/plans/{id}/allocation")
    public ApiResponse<PlanResponse> adjustAssignments(
            @PathVariable("id") Long planId, @Valid @RequestBody AdjustAssignmentsRequest request) {
        return ApiResponse.success(
                apiMapper.toResponse(allocationService.adjustAssignments(planId, apiMapper.toCommand(request))));
    }

    // 9. Xác nhận nhập học chính thức
    @PostMapping("/plans/{id}/confirm")
    public ApiResponse<ConfirmationResponse> confirmEnrollment(
            @PathVariable("id") Long planId, @Valid @RequestBody ConfirmEnrollmentRequest request) {
        return ApiResponse.success(
                apiMapper.toResponse(confirmationService.confirmEnrollment(planId, apiMapper.toCommand(request))));
    }

    // 10. Xem kết quả gửi thông báo
    @GetMapping("/plans/{id}/notifications")
    public ApiResponse<List<NotificationResponse>> listPlanNotifications(@PathVariable("id") Long planId) {
        planningService.getPlan(planId);
        return ApiResponse.success(notificationQueryService.listPlanNotifications(planId).stream()
                .map(apiMapper::toResponse)
                .toList());
    }
}
