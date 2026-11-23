package com.eduhub.edu_hub.admission.api.controller;

import com.eduhub.edu_hub.admission.api.dto.request.*;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionPlanResponse;
import com.eduhub.edu_hub.admission.application.service.*;
import com.eduhub.edu_hub.shared.common.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Các thao tác phát sinh trên cùng plan; mọi lần ghi dùng revision mới nhất và bảo toàn ENROLLED. */
@RestController
@RequestMapping("/api/v1/student-admissions/plans")
@RequiredArgsConstructor
public class AdmissionPlanAdjustmentController {
    private final ApiResponseFactory apiResponseFactory;
    private final AdmissionImportService importService;
    private final AdmissionApplicantCorrectionService correctionService;
    private final AdmissionAllocationService allocationService;

    // 1. File chỉ chứa phần bổ sung. Bản giống được bỏ qua, mâu thuẫn được giữ thành lỗi; không ghi đè dòng cũ.
    @PostMapping(value = "/{id}/applicants", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdmissionPlanResponse> importApplicants(
            @PathVariable("id") Long planId, @RequestParam long expectedRevision, @RequestParam MultipartFile file) {
        return apiResponseFactory.success(
                importService.importApplicants(planId, new ImportAdmissionApplicantsRequest(expectedRevision, file)));
    }

    // 2. Sửa đúng dòng INVALID/CONFLICT theo applicantId, kể cả khi mã hồ sơ/CCCD của dòng đang thiếu hoặc trùng.
    // Sau sửa, BE đối chiếu lại nhóm liên quan và tự phân bổ phần chờ; không cập nhật Student/User chính thức.
    @PatchMapping("/{planId}/applicants/{applicantId}")
    public ApiResponse<AdmissionPlanResponse> updateApplicant(
            @PathVariable Long planId,
            @PathVariable Long applicantId,
            @Valid @RequestBody UpdateAdmissionApplicantRequest request) {
        return apiResponseFactory.success(correctionService.updateApplicant(planId, applicantId, request));
    }

    // Bỏ dòng INVALID/CONFLICT bằng xóa mềm; BE đối chiếu lại các dòng còn lại và phân bổ phần chờ.
    // expectedRevision gửi trên query; dòng đã bỏ không còn xuất hiện trong response của plan.
    @DeleteMapping("/{planId}/applicants/{applicantId}")
    public ApiResponse<AdmissionPlanResponse> deleteApplicant(
            @PathVariable Long planId,
            @PathVariable Long applicantId,
            @Valid @ModelAttribute DeleteAdmissionApplicantRequest request) {
        return apiResponseFactory.success(correctionService.deleteApplicant(planId, applicantId, request));
    }

    // 3. Nếu cần, điều chỉnh phân bổ thủ công cho các hồ sơ chưa xác nhận nhập học.
    @PatchMapping("/{id}/allocation")
    public ApiResponse<AdmissionPlanResponse> adjustAssignments(
            @PathVariable("id") Long planId, @Valid @RequestBody AdjustAdmissionAssignmentsRequest request) {
        return apiResponseFactory.success(allocationService.adjustAssignments(planId, request));
    }
}
