package com.eduhub.edu_hub.identity.api.controller;

import com.eduhub.edu_hub.identity.api.dto.request.LockBody;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.AdminUserService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUserController {
    private final AdminUserService service;

    @GetMapping
    public ApiResponse<PageResponse<UserInfoResponse>> search(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return ApiResponse.success(service.search(token(authorization), keyword, page, size));
    }

    @PostMapping("/{userId}/lock")
    public ApiResponse<Void> lock(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long userId,
            @RequestBody LockBody body) {
        if (body == null || body.reason() == null || body.reason().isBlank()) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }
        service.lock(token(authorization), userId, body.reason());
        return ApiResponse.successWithMessage("Đã khóa tài khoản");
    }

    @PostMapping("/{userId}/unlock")
    public ApiResponse<Void> unlock(@RequestHeader("Authorization") String authorization, @PathVariable Long userId) {
        service.unlock(token(authorization), userId);
        return ApiResponse.successWithMessage("Đã mở khóa tài khoản");
    }

    @PostMapping("/{userId}/reset-password")
    public ApiResponse<Void> reset(@RequestHeader("Authorization") String authorization, @PathVariable Long userId) {
        service.resetPassword(token(authorization), userId);
        return ApiResponse.successWithMessage("Đã đặt lại mật khẩu và gửi email");
    }

    private String token(String authorization) {
        return authorization.substring(7);
    }
}
