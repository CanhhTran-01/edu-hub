package com.eduhub.edu_hub.curriculum.api.controller;

import com.eduhub.edu_hub.curriculum.api.dto.request.CreateSubjectRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.SetPrerequisiteRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.SubjectActionRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.SubjectSearchRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.UpdateSubjectRequest;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectResponse;
import com.eduhub.edu_hub.curriculum.application.SubjectApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
public class SubjectController {
    private final SubjectApplicationService subjectApplicationService;

    // TODO(CURRICULUM_VALIDATION): curriculum(n)--(n)subject, cần kiểm tra recommend_semester <= duration_semesters
    //  và subject/major/curriculum chưa bị ẩn hoặc ngừng sử dụng trước khi thay đổi quan hệ

    @GetMapping
    public ApiResponse<PageResponse<SubjectResponse>> searchSubjects(
            @Valid @ModelAttribute SubjectSearchRequest request) {
        PageResponse<SubjectResponse> subjects = subjectApplicationService.searchSubjects(request);
        return ApiResponse.<PageResponse<SubjectResponse>>builder()
                .success(true)
                .message(subjects.getContent().isEmpty() ? "Không tìm thấy kết quả" : "Success")
                .data(subjects)
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<SubjectResponse> getSubjectById(@PathVariable Long id) {
        return ApiResponse.success(subjectApplicationService.getSubjectById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubjectResponse>> createSubject(
            @Valid @RequestBody CreateSubjectRequest request) {
        SubjectResponse subject = subjectApplicationService.createSubject(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SubjectResponse>builder()
                        .success(true)
                        .message("Thêm học phần thành công")
                        .data(subject)
                        .build());
    }

    @PutMapping("/{id}")
    public ApiResponse<SubjectResponse> updateSubject(
            @PathVariable Long id, @Valid @RequestBody UpdateSubjectRequest request) {
        SubjectResponse subject = subjectApplicationService.updateSubject(id, request);
        return ApiResponse.<SubjectResponse>builder()
                .success(true)
                .message("Cập nhật học phần thành công")
                .data(subject)
                .build();
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<SubjectResponse> deactivateSubject(
            @PathVariable Long id, @Valid @RequestBody SubjectActionRequest request) {
        SubjectResponse subject = subjectApplicationService.deactivateSubject(id, request);
        return ApiResponse.<SubjectResponse>builder()
                .success(true)
                .message("Ngừng sử dụng học phần thành công")
                .data(subject)
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSubject(@PathVariable Long id, @Valid @RequestBody SubjectActionRequest request) {
        subjectApplicationService.deleteSubject(id, request);
        return ApiResponse.successWithMessage("Xóa học phần thành công");
    }

    @PostMapping("/{id}/prerequisites")
    public ApiResponse<SubjectResponse> setPrerequisites(
            @PathVariable Long id, @Valid @RequestBody SetPrerequisiteRequest request) {
        SubjectResponse subject = subjectApplicationService.setPrerequisites(id, request);
        return ApiResponse.<SubjectResponse>builder()
                .success(true)
                .message("Lưu môn tiên quyết thành công. Ràng buộc trùng (nếu có) được bỏ qua; "
                        + "thay đổi chỉ áp dụng từ thời điểm lưu, không hồi tố dữ liệu đã có.")
                .data(subject)
                .build();
    }
}
