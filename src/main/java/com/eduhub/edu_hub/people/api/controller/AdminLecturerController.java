package com.eduhub.edu_hub.people.api.controller;

import com.eduhub.edu_hub.people.api.dto.request.CreateLecturerRequest;
import com.eduhub.edu_hub.people.api.dto.request.LecturerSearchRequest;
import com.eduhub.edu_hub.people.api.dto.response.CreateLecturerResponse;
import com.eduhub.edu_hub.people.api.dto.response.LecturerNotificationResponse;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.service.LecturerApplicationService;
import com.eduhub.edu_hub.people.application.service.LecturerCreationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lecturers")
@RequiredArgsConstructor
public class AdminLecturerController {
    private final LecturerApplicationService lecturerApplicationService;
    private final LecturerCreationService lecturerCreationService;

    @GetMapping
    public ApiResponse<PageResponse<LecturerResponse>> search(
            LecturerSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1) throw new BaseException(ErrorCode.VALIDATION_ERROR);
        PageResponse<LecturerResponse> result = lecturerApplicationService.search(request, page, size);
        if (result.getContent().isEmpty()) {
            return ApiResponse.<PageResponse<LecturerResponse>>builder()
                    .success(true)
                    .message("Không tìm thấy kết quả")
                    .data(result)
                    .build();
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<LecturerResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(lecturerApplicationService.findById(id));
    }

    @PostMapping
    public ApiResponse<CreateLecturerResponse> create(@Valid @RequestBody CreateLecturerRequest request) {
        return ApiResponse.success(lecturerCreationService.create(request));
    }

    @PostMapping("/{id}/account-notification/resend")
    public ApiResponse<LecturerNotificationResponse> resend(@PathVariable Long id) {
        return ApiResponse.success(lecturerCreationService.resend(id));
    }
}
