package com.eduhub.edu_hub.admission.api.controller;

import com.eduhub.edu_hub.admission.api.dto.request.AdjustAdmissionAssignmentsRequest;
import com.eduhub.edu_hub.admission.api.dto.request.ConfirmAdmissionEnrollmentRequest;
import com.eduhub.edu_hub.admission.api.dto.request.CreateAdmissionPlanFromFileRequest;
import com.eduhub.edu_hub.admission.api.dto.request.ImportAdmissionApplicantsRequest;
import com.eduhub.edu_hub.admission.api.dto.request.PrepareAdmissionClassesRequest;
import com.eduhub.edu_hub.admission.api.dto.request.PreviewAdmissionAllocationRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdministrativeClassResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionCapacityResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionConfirmationResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.service.AdmissionAllocationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionClassPreparationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionConfirmationService;
import com.eduhub.edu_hub.admission.application.service.AdmissionImportService;
import com.eduhub.edu_hub.admission.application.service.AdmissionNotificationQueryService;
import com.eduhub.edu_hub.admission.application.service.AdmissionPlanningService;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationResponse;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Các endpoint được xếp theo thứ tự thực hiện luồng nhập học từ file.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StudentAdmissionController {
    private final ApiResponseFactory apiResponseFactory;
    private final AdmissionPlanningService planningService;
    private final AdmissionImportService importService;
    private final AdmissionClassPreparationService classPreparationService;
    private final AdmissionAllocationService allocationService;
    private final AdmissionConfirmationService confirmationService;
    private final AdmissionNotificationQueryService notificationQueryService;

    // Cấu hình mặc định được đọc từ SystemConfigService dùng chung trong shared.

    // 1. Lấy sĩ số tối thiểu, tối đa và dự phòng mặc định để điền màn nhập học.
    @GetMapping("/student-admissions/configuration")
    public ApiResponse<AdmissionCapacityResponse> getDefaultCapacityConfiguration() {
        return apiResponseFactory.success(planningService.getDefaultCapacityConfiguration());
    }

    // 2. Chọn ngành và khóa, sau đó tải mẫu XLSX để nhập danh sách đúng phạm vi.
    @GetMapping("/student-admissions/template")
    public ResponseEntity<byte[]> downloadImportTemplate(@RequestParam Long majorId, @RequestParam Long cohortId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=admission-template.xlsx")
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(planningService.generateImportTemplate(majorId, cohortId));
    }

    // 3. Upload file để lập phương án; số hồ sơ được tính vào đợt và các kịch bản do BE tính sau import.
    @PostMapping(value = "/student-admissions/plans/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdmissionPlanResponse> createPlanFromFile(
            @RequestParam Long majorId,
            @RequestParam Long cohortId,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) Integer maxCapacity,
            @RequestParam(required = false) Integer reserveCapacity,
            @RequestParam MultipartFile file) {
        return apiResponseFactory.success(planningService.createPlanFromFile(new CreateAdmissionPlanFromFileRequest(
                majorId, cohortId, minCapacity, maxCapacity, reserveCapacity, file)));
    }

    // 4. Xem lại phương án, lỗi từng hồ sơ và các kịch bản trong trường scenarios.
    @GetMapping("/student-admissions/plans/{id}")
    public ApiResponse<AdmissionPlanResponse> getPlan(@PathVariable("id") Long planId) {
        return apiResponseFactory.success(planningService.getPlan(planId));
    }

    // 5. Xem lớp hành chính hiện có và cờ availableForAdmission để chọn lớp dùng lại; đây không phải kịch bản.
    // TODO: FE cần hiển thị dropdown chọn kịch bản từ scenarios của bước 3/4 trước khi chuẩn bị lớp ở bước 6.
    @GetMapping("/administrative-classes")
    public ApiResponse<List<AdministrativeClassResponse>> listAdministrativeClasses(
            @RequestParam Long majorId, @RequestParam Long cohortId) {
        return apiResponseFactory.success(classPreparationService.listAdministrativeClasses(majorId, cohortId));
    }

    // 6. Chọn kịch bản và chuẩn bị lớp: tự dùng lại lớp trống, chọn lớp cụ thể hoặc tạo mới theo request.
    @PostMapping("/administrative-classes/from-admission-plan")
    public ApiResponse<AdmissionPlanResponse> prepareClasses(
            @Valid @RequestBody PrepareAdmissionClassesRequest request) {
        return apiResponseFactory.success(classPreparationService.prepareClasses(request));
    }

    // 7. Khi cần sửa/bổ sung file, import lại trên cùng phương án với revision mới nhất; giữ hồ sơ đã ENROLLED.
    @PostMapping(value = "/student-admissions/plans/{id}/applicants", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdmissionPlanResponse> importApplicants(
            @PathVariable("id") Long planId, @RequestParam long expectedRevision, @RequestParam MultipartFile file) {
        return apiResponseFactory.success(
                importService.importApplicants(planId, new ImportAdmissionApplicantsRequest(expectedRevision, file)));
    }

    // 8. Khi đã có danh sách và lớp, chạy phân bổ rồi xem trước sĩ số, điểm và cảnh báo từng lớp.
    @PostMapping("/student-admissions/plans/{id}/allocation/preview")
    public ApiResponse<AdmissionPlanResponse> previewAllocation(
            @PathVariable("id") Long planId, @Valid @RequestBody PreviewAdmissionAllocationRequest request) {
        return apiResponseFactory.success(allocationService.previewAllocation(planId, request));
    }

    // 9. Nếu cần, điều chỉnh phân bổ thủ công cho các hồ sơ chưa xác nhận nhập học.
    @PatchMapping("/student-admissions/plans/{id}/allocation")
    public ApiResponse<AdmissionPlanResponse> adjustAssignments(
            @PathVariable("id") Long planId, @Valid @RequestBody AdjustAdmissionAssignmentsRequest request) {
        return apiResponseFactory.success(allocationService.adjustAssignments(planId, request));
    }

    // 10. Chốt nhập học: tạo/dùng lại sinh viên, tài khoản và thông báo theo từng hồ sơ; có thể thành công một phần.
    @PostMapping("/student-admissions/plans/{id}/confirm")
    public ApiResponse<AdmissionConfirmationResponse> confirmEnrollment(
            @PathVariable("id") Long planId, @Valid @RequestBody ConfirmAdmissionEnrollmentRequest request) {
        return apiResponseFactory.success(confirmationService.confirmEnrollment(planId, request));
    }

    // 11. Xem trạng thái gửi thông báo sau xác nhận; gửi mail lỗi không hoàn tác hồ sơ đã nhập học thành công.
    @GetMapping("/student-admissions/plans/{id}/notifications")
    public ApiResponse<List<AdmissionNotificationResponse>> listPlanNotifications(@PathVariable("id") Long planId) {
        planningService.getPlan(planId);
        return apiResponseFactory.success(notificationQueryService.listPlanNotifications(planId));
    }
}
