package com.eduhub.edu_hub.identity.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
}
