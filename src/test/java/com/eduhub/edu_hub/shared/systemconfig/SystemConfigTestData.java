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
        // Mapping minh họa chỉ dành cho unit test; runtime bắt đầu với mapping rỗng và phải được quản trị thiết lập.
        configuredValues.put(
                SystemConfigKey.MAJOR_CODE_PREFIXES,
                "{\"CNTT\":\"CT\",\"ATTT\":\"AT\",\"DTVT\":\"DT\",\"TTNT\":\"TT\",\"L\":\"L\"}");
        configuredValues.put(SystemConfigKey.FACULTY_CODE_PREFIXES, "{\"CNTT\":\"CT\",\"ATTT\":\"AT\"}");
        configuredValues.put(
                SystemConfigKey.TRAINING_TYPE_CODE_PREFIXES,
                "{\"CHINH_QUY\":\"CQ\",\"LIEN_THONG\":\"LT\",\"VAN_BANG_2\":\"VB2\"}");
        configuredValues.putAll(overrides);
        configuredValues.replaceAll((key, configuredValue) -> key.getDataType() == SystemConfigKey.DataType.DURATION
                ? DurationStyle.detectAndParse(configuredValue).toString()
                : configuredValue);
        return new SystemConfigSnapshotResponse(configuredValues);
    }
}
