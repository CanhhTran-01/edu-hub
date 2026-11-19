package com.eduhub.edu_hub.shared.systemconfig.api.dto.response;

import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.time.LocalDateTime;

public record SystemConfigResponse(
        SystemConfigKey key,
        SystemConfigKey.Group group,
        SystemConfigKey.DataType dataType,
        String label,
        String value,
        boolean configured,
        long revision,
        LocalDateTime updatedAt,
        String updatedBy) {}
