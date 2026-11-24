package com.eduhub.edu_hub.admission.api.controller;

import com.eduhub.edu_hub.admission.api.dto.request.*;
import com.eduhub.edu_hub.admission.api.dto.response.*;
import com.eduhub.edu_hub.admission.application.exception.AdmissionApplicantException;
import com.eduhub.edu_hub.admission.application.service.*;
import com.eduhub.edu_hub.shared.common.*;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Các endpoint được xếp theo thứ tự thực hiện luồng nhập học từ file. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AdmissionController {
    private final ApiResponseFactory apiResponseFactory;
    private final AdmissionPlanningService planningService;
    private final AdmissionConfirmationService confirmationService;

    // Cấu hình mặc định được đọc từ SystemConfigService dùng chung trong shared.
    // 1. FE chỉ hiển thị ba thông số; plan đã tồn tại hiển thị capacity đã chốt trong response của chính plan.
    @GetMapping("/student-admissions/configuration")
    public ApiResponse<AdmissionCapacityResponse> getDefaultCapacityConfiguration() {
        return apiResponseFactory.success(planningService.getDefaultCapacityConfiguration());
    }

    // 2. Chọn ngành và khóa, sau đó tải mẫu XLSX để nhập danh sách đúng phạm vi.
    // FE lấy dropdown từ GET /api/v1/majors và /api/v1/cohorts; mẫu chốt cả mã ngành và mã khóa đã chọn.
    @GetMapping("/student-admissions/template")
    public ResponseEntity<byte[]> downloadImportTemplate(@RequestParam Long majorId, @RequestParam Long cohortId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=admission-template.xlsx")
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(planningService.generateImportTemplate(majorId, cohortId));
    }

    // 3. Mở màn bằng ngành–khóa: plan=null hiển thị upload ban đầu; đã có plan thì tiếp tục trạng thái đã lưu.
    // GET không tự tạo plan và không chạy lại phân bổ khi xem các khóa cũ.
    @GetMapping("/student-admissions/plans/by-scope")
    public ApiResponse<AdmissionPlanLookupResponse> findPlanByScope(
            @RequestParam Long majorId, @RequestParam Long cohortId) {
        return apiResponseFactory.success(planningService.findPlanByScope(majorId, cohortId));
    }

    // 4. Upload lần đầu: BE chốt SystemConfig, đọc/đối chiếu file rồi tự chuẩn bị và phân lớp.
    // Bước này chỉ tạo plan, hồ sơ chờ và lớp; Student/User/MSSV chỉ được ghi khi xác nhận.
    @PostMapping(value = "/student-admissions/plans/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdmissionPlanResponse> createPlanFromFile(
            @RequestParam Long majorId,
            @RequestParam Long cohortId,
            @RequestParam MultipartFile file,
            @RequestParam Map<String, String> formParameters) {
        var unsupportedFields = formParameters.keySet().stream()
                .filter(field -> !Set.of("majorId", "cohortId", "file").contains(field))
                .map(field -> new FieldErrorDetail(field, "Upload ban đầu chỉ nhận majorId, cohortId và file"))
                .toList();
        if (!unsupportedFields.isEmpty())
            throw new AdmissionApplicantException(ErrorCode.VALIDATION_ERROR, unsupportedFields);
        return apiResponseFactory.success(
                planningService.createPlanFromFile(new CreateAdmissionPlanFromFileRequest(majorId, cohortId, file)));
    }

    // 5. Xem lại plan, lỗi từng hồ sơ, phân bổ và cảnh báo. canConfirm là gợi ý UI, BE vẫn kiểm tra lại khi ghi.
    @GetMapping("/student-admissions/plans/{id}")
    public ApiResponse<AdmissionPlanResponse> getPlan(@PathVariable("id") Long planId) {
        return apiResponseFactory.success(planningService.getPlan(planId));
    }

    // 6. Chốt nhập học: tạo/dùng lại sinh viên, tài khoản và thông báo theo từng hồ sơ; có thể thành công một phần.
    // FE chấp nhận tường minh dự phòng/dưới tối thiểu; không tự bật các cờ theo cảnh báo.
    // Kết quả trả planId để mở tab tiến trình mail; phần lỗi nhập học vẫn hiển thị tại tab gốc.
    @PostMapping("/student-admissions/plans/{id}/confirm")
    public ApiResponse<AdmissionConfirmationResponse> confirmEnrollment(
            @PathVariable("id") Long planId, @Valid @RequestBody ConfirmAdmissionEnrollmentRequest request) {
        return apiResponseFactory.success(confirmationService.confirmEnrollment(planId, request));
    }
}
