package com.eduhub.edu_hub.shared.systemconfig.api.dto.request;

import com.eduhub.edu_hub.shared.systemconfig.api.serialization.SystemConfigRevisionDeserializer;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public record UpdateSystemConfigsRequest(
        @NotEmpty @Size(max = 22) List<@NotNull @Valid ConfigurationUpdateRequest> configurations) {

    public record ConfigurationUpdateRequest(
            @NotNull SystemConfigKey key,
            @NotNull @Size(max = 4096) String value,
            @NotNull @Min(0) @JsonDeserialize(using = SystemConfigRevisionDeserializer.class) Long expectedRevision) {}
}
