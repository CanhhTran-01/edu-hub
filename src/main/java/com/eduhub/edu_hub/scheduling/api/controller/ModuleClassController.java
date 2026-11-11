package com.eduhub.edu_hub.scheduling.api.controller;

import com.eduhub.edu_hub.scheduling.api.dto.request.CancelModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateModuleClassesRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.UpdateModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.OpeningSubjectResponse;
import com.eduhub.edu_hub.scheduling.application.service.ModuleClassApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ModuleClassController {
    private final ModuleClassApplicationService moduleClassApplicationService;

    // 1. Lọc lấy các môn học cần mở lớp học phần tại kì hiện tại
    @GetMapping("/semesters/{semesterId}/opening-subjects")
    public ApiResponse<List<OpeningSubjectResponse>> findSubjectsToOpen(@PathVariable Long semesterId) {
        var subjects = moduleClassApplicationService.findSubjectsToOpen(semesterId);
        return ApiResponse.<List<OpeningSubjectResponse>>builder()
                .success(true)
                .message(subjects.isEmpty() ? "Không có học phần phù hợp để đề xuất mở lớp" : "Success")
                .data(subjects)
                .build();
    }

    // 2. Tạo lớp học phần dựa trên dữ liệu thu được từ bước 1
    @PostMapping("/semesters/{semesterId}/module-classes/batch")
    public ResponseEntity<ApiResponse<List<ModuleClassResponse>>> createBatch(
            @PathVariable Long semesterId, @Valid @RequestBody CreateModuleClassesRequest request) {
        var classes = moduleClassApplicationService.createModuleClasses(semesterId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<List<ModuleClassResponse>>builder()
                        .success(true)
                        .message("Mở lớp học phần thành công")
                        .data(classes)
                        .build());
    }

    // 3. Search lớp học phần, TODO: trả về simple response hiển thị trong list
    @GetMapping("/module-classes")
    public ApiResponse<PageResponse<ModuleClassResponse>> search(
            @Valid @ModelAttribute ModuleClassSearchRequest request) {
        return ApiResponse.success(moduleClassApplicationService.searchModuleClasses(request));
    }

    // 4. Xem chi tiết lớp học phần khi bấm vào list đó
    @GetMapping("/module-classes/{id}")
    public ApiResponse<ModuleClassResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(moduleClassApplicationService.findModuleClassById(id));
    }

    // 5. Cập nhật sức chứa kèm lý do
    @PatchMapping("/module-classes/{id}")
    public ApiResponse<ModuleClassResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateModuleClassRequest request) {
        return ApiResponse.<ModuleClassResponse>builder()
                .success(true)
                .message("Cập nhật lớp học phần thành công")
                .data(moduleClassApplicationService.updateModuleClassCapacity(id, request))
                .build();
    }

    // 6. Hủy lớp học phần
    @PostMapping("/module-classes/{id}/cancel")
    public ApiResponse<ModuleClassResponse> cancel(
            @PathVariable Long id, @Valid @RequestBody CancelModuleClassRequest request) {
        return ApiResponse.<ModuleClassResponse>builder()
                .success(true)
                .message("Hủy lớp học phần thành công")
                .data(moduleClassApplicationService.cancelModuleClass(id, request))
                .build();
    }
}
