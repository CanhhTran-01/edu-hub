package com.eduhub.edu_hub.shared.systemconfig;

import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.shared.systemconfig.api.dto.response.SystemConfigSnapshotResponse;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.convert.DurationStyle;

/** Bản chụp chỉ dùng trong unit test; ngưỡng tín chỉ minh họa không phải mặc định runtime. */
public final class SystemConfigTestData {
    private SystemConfigTestData() {}

    public static SystemConfigService configuredService() {
        return configuredService(Map.of());
    }

    public static SystemConfigService configuredService(Map<SystemConfigKey, String> overrides) {
        var service = mock(SystemConfigService.class);
        lenient().when(service.getSnapshot()).thenReturn(snapshot(overrides));
        return service;
    }

    public static SystemConfigSnapshotResponse snapshot(Map<SystemConfigKey, String> overrides) {
        Map<SystemConfigKey, String> configuredValues = new EnumMap<>(SystemConfigKey.class);
        for (var key : SystemConfigKey.values())
            if (key.getDefaultValue() != null) configuredValues.put(key, key.getDefaultValue());
        configuredValues.put(SystemConfigKey.ENROLLMENT_MINIMUM_CREDITS, "12");
        configuredValues.put(SystemConfigKey.ENROLLMENT_MAXIMUM_CREDITS, "20");
        configuredValues.putAll(overrides);
        configuredValues.replaceAll((key, configuredValue) -> key.getDataType() == SystemConfigKey.DataType.DURATION
                ? DurationStyle.detectAndParse(configuredValue).toString()
                : configuredValue);
        return new SystemConfigSnapshotResponse(configuredValues);
    }
}
