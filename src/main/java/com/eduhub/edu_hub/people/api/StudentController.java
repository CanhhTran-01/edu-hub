package com.eduhub.edu_hub.people.api;

import com.eduhub.edu_hub.people.api.dto.StudentResponse;
import com.eduhub.edu_hub.people.api.dto.StudentSearchRequest;
import com.eduhub.edu_hub.people.api.dto.UpdateStudentRequest;
import com.eduhub.edu_hub.people.application.StudentApplicationService;
import com.eduhub.edu_hub.people.application.StudentAdmissionService;
import com.eduhub.edu_hub.people.api.dto.AdmissionClassAdjustmentRequest;
import com.eduhub.edu_hub.people.api.dto.AdmissionConfirmRequest;
import com.eduhub.edu_hub.people.api.dto.AdmissionConfirmResponse;
import com.eduhub.edu_hub.people.api.dto.AdmissionPreviewResponse;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentController {
    private final StudentApplicationService studentService;
    private final StudentAdmissionService admissionService;

    @PostMapping(value = "/admission/preview", consumes = "multipart/form-data")
    public ApiResponse<AdmissionPreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(admissionService.preview(file));
    }

    @PutMapping("/admission/{batchId}/assignment")
    public ApiResponse<AdmissionPreviewResponse> adjustAssignment(
            @PathVariable UUID batchId, @Valid @RequestBody AdmissionClassAdjustmentRequest request) {
        return ApiResponse.success(admissionService.adjust(batchId, request));
    }

    @PostMapping("/admission/{batchId}/confirm")
    public ApiResponse<AdmissionConfirmResponse> confirm(
            @PathVariable UUID batchId, @RequestBody AdmissionConfirmRequest request) {
        return ApiResponse.success(admissionService.confirm(batchId, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<StudentResponse>> search(
            StudentSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }
        PageResponse<StudentResponse> result = studentService.search(request, page, size);
        if (result.getContent().isEmpty()) {
            return ApiResponse.<PageResponse<StudentResponse>>builder()
                    .success(true)
                    .message("Không tìm thấy kết quả")
                    .data(result)
                    .build();
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(studentService.findById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudentResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateStudentRequest request) {
        return ApiResponse.success(studentService.update(id, request));
    }
}
