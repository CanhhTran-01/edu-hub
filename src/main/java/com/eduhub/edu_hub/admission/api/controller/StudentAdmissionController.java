package com.eduhub.edu_hub.admission.api.controller;

import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.AdjustAssignmentsRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.ConfirmRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.PlanRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.PreviewRequest;
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
    private final AdmissionApiMapper mapper;
    private final AdmissionPlanningService planning;
    private final AdmissionImportService imports;
    private final AdmissionAllocationService allocation;
    private final AdmissionConfirmationService confirmation;
    private final AdmissionNotificationQueryService notifications;
    private final AdmissionSettings settings;

    // 1. Lấy cấu hình mặc định: sĩ số tối đa, sĩ số tối thiểu, suất dự bị
    @GetMapping("/configuration")
    public ApiResponse<Capacity> configuration() {
        return ApiResponse.success(
                new Capacity(settings.getMinCapacity(), settings.getMaxCapacity(), settings.getReserveCapacity()));
    }

    // 2. Chọn ngành và khóa trước khi tải file Excel mẫu tương ứng
    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam Long majorId, @RequestParam Long cohortId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=admission-template.xlsx")
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(planning.template(majorId, cohortId));
    }

    // 3. Lập phương án từ số sinh viên dự kiến
    // Dùng khi Admin đã biết số lượng dự kiến nhưng chưa có DS chi tiết. Chưa tạo lớp, sinh viên, tài khoản
    @PostMapping("/plans")
    public ApiResponse<PlanResponse> create(@Valid @RequestBody PlanRequest request) {
        return ApiResponse.success(mapper.toResponse(planning.create(mapper.toCommand(request))));
    }

    // 4. Lập phương án mới từ file tuyển sinh
    @PostMapping(value = "/plans/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PlanResponse> createFromFile(
            @RequestParam Long majorId,
            @RequestParam Long cohortId,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) Integer maxCapacity,
            @RequestParam(required = false) Integer reserveCapacity,
            @RequestParam MultipartFile file) {
        return ApiResponse.success(mapper.toResponse(planning.createWithFile(
                mapper.toCommand(new PlanRequest(majorId, cohortId, 0, minCapacity, maxCapacity, reserveCapacity)),
                file)));
    }

    // 5. Xem lại plan
    @GetMapping("/plans/{id}")
    public ApiResponse<PlanResponse> get(@PathVariable Long id) {
        return ApiResponse.success(mapper.toResponse(planning.get(id)));
    }

    // 6. Nhập danh sách vào plan đã có
    // sử dụng plan hiện có, không tạo mới, hồ sơ đã nhập học thành công được giữ nguyên khi xử lý phần nhập lại
    @PostMapping(value = "/plans/{id}/applicants", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PlanResponse> upload(
            @PathVariable Long id, @RequestParam long expectedRevision, @RequestParam MultipartFile file) {
        return ApiResponse.success(mapper.toResponse(imports.upload(id, expectedRevision, file)));
    }

    // 7. Chạy phân bổ và tạo bản xem trước
    @PostMapping("/plans/{id}/allocation/preview")
    public ApiResponse<PlanResponse> preview(@PathVariable Long id, @Valid @RequestBody PreviewRequest request) {
        return ApiResponse.success(mapper.toResponse(allocation.preview(id, mapper.toCommand(request))));
    }

    // 8. Điều chỉnh phân bổ thủ công
    @PatchMapping("/plans/{id}/allocation")
    public ApiResponse<PlanResponse> adjust(
            @PathVariable Long id, @Valid @RequestBody AdjustAssignmentsRequest request) {
        return ApiResponse.success(mapper.toResponse(allocation.adjust(id, mapper.toCommand(request))));
    }

    // 9. Xác nhận nhập học chính thức
    @PostMapping("/plans/{id}/confirm")
    public ApiResponse<ConfirmationResponse> confirm(
            @PathVariable Long id, @Valid @RequestBody ConfirmRequest request) {
        return ApiResponse.success(mapper.toResponse(confirmation.confirm(id, mapper.toCommand(request))));
    }

    // 10. Xem kết quả gửi thông báo
    @GetMapping("/plans/{id}/notifications")
    public ApiResponse<List<NotificationResponse>> notifications(@PathVariable Long id) {
        planning.get(id);
        return ApiResponse.success(
                notifications.list(id).stream().map(mapper::toResponse).toList());
    }
}
