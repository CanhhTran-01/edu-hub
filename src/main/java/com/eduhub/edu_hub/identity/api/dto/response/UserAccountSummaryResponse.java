package com.eduhub.edu_hub.identity.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserAccountSummaryResponse {
    private Long id;
    private String username;
    private String role;
    private String status;
}
