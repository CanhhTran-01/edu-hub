package com.eduhub.edu_hub.identity.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    @Builder.Default
    private String tokenType = "Bearer";

    private String accessToken;
    private String refreshToken;

    private long expiresIn;
    private boolean requirePasswordChange;
    private UserInfoResponse user;
}
