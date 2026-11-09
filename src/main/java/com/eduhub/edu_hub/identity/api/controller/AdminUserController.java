package com.eduhub.edu_hub.identity.api.controller;

import com.eduhub.edu_hub.identity.api.dto.request.LockUserRequest;
import com.eduhub.edu_hub.identity.api.dto.request.SearchUserAccountsRequest;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.service.AdminUserService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService adminUserService;

    // TODO(AUTHORIZATION): Khi triển khai phân quyền, chỉ SYSTEM_ADMIN có permission quản trị tài khoản
    //  mới được tìm kiếm, khóa/mở khóa và reset mật khẩu ở đây. Hiện JWT ACCESS của role khác vẫn gọi được.
    //  Quyền kỹ thuật này không cho phép SYSTEM_ADMIN cập nhật hồ sơ Student qua API của People.

    // TODO: sau này sẽ chỉnh thành 1 flow hoàn chỉnh:
    //  admin vào dashboard --> FE hiện tài khoản --> admin tra cứu, xem chi tiết tài khoản mình cần --> thao tác

    @GetMapping
    public ApiResponse<PageResponse<UserInfoResponse>> searchUserAccounts(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Valid SearchUserAccountsRequest request) {
        return ApiResponse.success(adminUserService.searchUserAccounts(authenticatedJwt, request));
    }

    // TODO: cần 1 endpoints trả về response chi tiết hơn (Search sẽ trả UserInfoSimple)

    @PostMapping("/{userId}/lock")
    public ApiResponse<Void> lockUserAccount(
            @AuthenticationPrincipal Jwt authenticatedJwt,
            @PathVariable Long userId,
            @Valid @RequestBody LockUserRequest request) {
        adminUserService.lockUserAccount(authenticatedJwt, userId, request);
        return ApiResponse.successWithMessage("Đã khóa tài khoản");
    }

    @PostMapping("/{userId}/unlock")
    public ApiResponse<Void> unlockUserAccount(
            @AuthenticationPrincipal Jwt authenticatedJwt, @PathVariable Long userId) {
        adminUserService.unlockUserAccount(authenticatedJwt, userId);
        return ApiResponse.successWithMessage("Đã mở khóa tài khoản");
    }

    @PostMapping("/{userId}/reset-password")
    public ApiResponse<Void> resetUserPassword(
            @AuthenticationPrincipal Jwt authenticatedJwt, @PathVariable Long userId) {
        adminUserService.resetUserPassword(authenticatedJwt, userId);
        return ApiResponse.successWithMessage("Đã đặt lại mật khẩu và gửi email");
    }
}
