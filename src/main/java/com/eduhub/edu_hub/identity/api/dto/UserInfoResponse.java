package com.eduhub.edu_hub.identity.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoResponse {
    private Long id;
    private String username;
    private String role;
    private String fullName;
    private boolean isFirstLogin;
    private String status;
}
