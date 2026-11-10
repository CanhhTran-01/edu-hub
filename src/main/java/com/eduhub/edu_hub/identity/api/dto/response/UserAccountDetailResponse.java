package com.eduhub.edu_hub.identity.api.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserAccountDetailResponse {
    private Long id;
    private String username;
    private String role;
    private String status;
    private int failedLoginCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String email;
    private boolean firstLogin;
    private boolean temporarilyLocked;
    private String fullName;
    private String phone;
}
