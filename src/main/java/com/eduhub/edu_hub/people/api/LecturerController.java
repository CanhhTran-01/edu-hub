package com.eduhub.edu_hub.people.api;

import com.eduhub.edu_hub.people.api.dto.LecturerResponse;
import com.eduhub.edu_hub.people.api.dto.LecturerSearchRequest;
import com.eduhub.edu_hub.people.application.LecturerApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lecturers")
@RequiredArgsConstructor
public class LecturerController {
    private final LecturerApplicationService lecturerService;
    private final com.eduhub.edu_hub.people.application.LecturerCreationService creationService;

    @org.springframework.web.bind.annotation.PostMapping
    public ApiResponse<com.eduhub.edu_hub.people.api.dto.CreateLecturerResponse> create(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            com.eduhub.edu_hub.people.api.dto.CreateLecturerRequest request,
            @org.springframework.web.bind.annotation.RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(creationService.create(request, token(authorization)));
    }

    @org.springframework.web.bind.annotation.PostMapping("/{id}/account-notification/resend")
    public ApiResponse<com.eduhub.edu_hub.people.api.dto.LecturerNotificationResponse> resend(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(creationService.resend(id, token(authorization)));
    }

    private String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7)
            throw new BaseException(ErrorCode.UNAUTHENTICATED);
        return authorization.substring(7);
    }

    @GetMapping
    public ApiResponse<PageResponse<LecturerResponse>> search(
            LecturerSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }
        PageResponse<LecturerResponse> result = lecturerService.search(request, page, size);
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
        return ApiResponse.success(lecturerService.findById(id));
    }
}
