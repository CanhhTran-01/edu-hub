package com.eduhub.edu_hub.scheduling.api.controller;

import com.eduhub.edu_hub.scheduling.api.dto.request.CancelModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateModuleClassesRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.UpdateModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.OpeningSubjectResponse;
import com.eduhub.edu_hub.scheduling.application.ModuleClassApplicationService;
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
    private final ModuleClassApplicationService service;

    @GetMapping("/semesters/{semesterId}/opening-subjects")
    public ApiResponse<List<OpeningSubjectResponse>> findOpeningSubjects(@PathVariable Long semesterId) {
        var subjects = service.findOpeningSubjects(semesterId);
        return ApiResponse.<List<OpeningSubjectResponse>>builder()
                .success(true)
                .message(subjects.isEmpty() ? "Không có học phần phù hợp để đề xuất mở lớp" : "Success")
                .data(subjects)
                .build();
    }

    @GetMapping("/module-classes")
    public ApiResponse<PageResponse<ModuleClassResponse>> search(
            @Valid @ModelAttribute ModuleClassSearchRequest request) {
        return ApiResponse.success(service.search(request));
    }

    @GetMapping("/module-classes/{id}")
    public ApiResponse<ModuleClassResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @PostMapping("/semesters/{semesterId}/module-classes/batch")
    public ResponseEntity<ApiResponse<List<ModuleClassResponse>>> createBatch(
            @PathVariable Long semesterId, @Valid @RequestBody CreateModuleClassesRequest request) {
        var classes = service.createBatch(semesterId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<List<ModuleClassResponse>>builder()
                        .success(true)
                        .message("Mở lớp học phần thành công")
                        .data(classes)
                        .build());
    }

    @PatchMapping("/module-classes/{id}")
    public ApiResponse<ModuleClassResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateModuleClassRequest request) {
        return ApiResponse.<ModuleClassResponse>builder()
                .success(true)
                .message("Cập nhật lớp học phần thành công")
                .data(service.update(id, request))
                .build();
    }

    @PostMapping("/module-classes/{id}/cancel")
    public ApiResponse<ModuleClassResponse> cancel(
            @PathVariable Long id, @Valid @RequestBody CancelModuleClassRequest request) {
        return ApiResponse.<ModuleClassResponse>builder()
                .success(true)
                .message("Hủy lớp học phần thành công")
                .data(service.cancel(id, request))
                .build();
    }
}
