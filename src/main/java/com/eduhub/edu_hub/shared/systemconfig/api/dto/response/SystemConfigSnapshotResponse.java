package com.eduhub.edu_hub.shared.systemconfig.api.dto.response;

import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.time.Duration;
import java.util.Map;

/** Bản chụp bất biến để các tham số liên quan của một thao tác được đọc cùng một lần. */
public record SystemConfigSnapshotResponse(Map<SystemConfigKey, String> values) {
    public SystemConfigSnapshotResponse {
        values = Map.copyOf(values);
    }

    public String getString(SystemConfigKey key) {
        String configuredValue = values.get(key);
        if (configuredValue == null) throw SystemConfigException.unavailable(key);
        return configuredValue;
    }

    public int getInteger(SystemConfigKey key) {
        return Integer.parseInt(getString(key));
    }

    public boolean getBoolean(SystemConfigKey key) {
        return Boolean.parseBoolean(getString(key));
    }

    public Duration getDuration(SystemConfigKey key) {
        return Duration.parse(getString(key));
    }
}
