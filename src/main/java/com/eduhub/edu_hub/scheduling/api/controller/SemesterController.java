package com.eduhub.edu_hub.scheduling.api.controller;

import com.eduhub.edu_hub.scheduling.api.dto.CreateSemesterRequest;
import com.eduhub.edu_hub.scheduling.api.dto.SemesterResponse;
import com.eduhub.edu_hub.scheduling.api.dto.SemesterSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.UpdateSemesterRequest;
import com.eduhub.edu_hub.scheduling.application.SemesterApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/semesters")
@RequiredArgsConstructor
public class SemesterController {
    private final SemesterApplicationService semesterApplicationService;

    @GetMapping
    public ApiResponse<PageResponse<SemesterResponse>> searchSemesters(
            @Valid @ModelAttribute SemesterSearchRequest request) {
        var semesters = semesterApplicationService.searchSemesters(request);
        return ApiResponse.<PageResponse<SemesterResponse>>builder()
                .success(true)
                .message(semesters.getContent().isEmpty() ? "Không tìm thấy kết quả" : "Success")
                .data(semesters)
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<SemesterResponse> getSemesterById(@PathVariable Long id) {
        return ApiResponse.success(semesterApplicationService.getSemesterById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SemesterResponse>> createSemester(
            @Valid @RequestBody CreateSemesterRequest request) {
        var semester = semesterApplicationService.createSemester(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SemesterResponse>builder()
                        .success(true)
                        .message("Thêm kỳ học thành công")
                        .data(semester)
                        .build());
    }

    @PutMapping("/{id}")
    public ApiResponse<SemesterResponse> updateSemester(
            @PathVariable Long id, @Valid @RequestBody UpdateSemesterRequest request) {
        var semester = semesterApplicationService.updateSemester(id, request);
        return ApiResponse.<SemesterResponse>builder()
                .success(true)
                .message("Cập nhật kỳ học thành công")
                .data(semester)
                .build();
    }
}
