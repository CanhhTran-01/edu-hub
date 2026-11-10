package com.eduhub.edu_hub.identity.api.dto.request;

import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SearchUserAccountsRequest {
    private String keyword = "";

    private RoleName role;
    private UserStatus status;
    private Boolean firstLogin;

    @Min(value = 0, message = "Số trang phải từ 0")
    private int page = 0;

    @Min(value = 1, message = "Kích thước trang phải từ 1")
    private int size = 20;
}
