package com.eduhub.edu_hub.identity.api;

import com.eduhub.edu_hub.identity.api.dto.LoginRequest;
import com.eduhub.edu_hub.identity.api.dto.LoginResponse;
import com.eduhub.edu_hub.identity.api.dto.LogoutRequest;
import com.eduhub.edu_hub.identity.application.AuthApplicationService;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody LogoutRequest request) {
        authService.logout(authorization.substring(7), request.getRefreshToken());
        return ApiResponse.successWithMessage("Đăng xuất thành công");
    }
}
