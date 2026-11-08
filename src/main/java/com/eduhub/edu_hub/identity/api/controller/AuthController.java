package com.eduhub.edu_hub.identity.api.controller;

import com.eduhub.edu_hub.identity.api.dto.request.ChangePasswordRequest;
import com.eduhub.edu_hub.identity.api.dto.request.LoginRequest;
import com.eduhub.edu_hub.identity.api.dto.request.LogoutRequest;
import com.eduhub.edu_hub.identity.api.dto.response.LoginResponse;
import com.eduhub.edu_hub.identity.application.service.AuthApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthApplicationService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Valid @RequestBody LogoutRequest request) {
        authService.logout(authenticatedJwt, request.getRefreshToken());
        return ApiResponse.successWithMessage("Đăng xuất thành công");
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Jwt authenticatedJwt, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authenticatedJwt, request);
        return ApiResponse.successWithMessage("Đổi mật khẩu thành công. Vui lòng đăng nhập lại.");
    }
}
