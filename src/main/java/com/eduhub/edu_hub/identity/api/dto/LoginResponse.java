package com.eduhub.edu_hub.identity.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UserInfoResponse user;
    private boolean requirePasswordChange;
}
