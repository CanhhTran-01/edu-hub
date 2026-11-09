package com.eduhub.edu_hub.identity.api.dto.response;

/** Email hiện tại của tài khoản; không đưa entity hoặc thông tin xác thực sang module hồ sơ. */
public record UserAccountEmailResponse(Long userId, String email) {}
