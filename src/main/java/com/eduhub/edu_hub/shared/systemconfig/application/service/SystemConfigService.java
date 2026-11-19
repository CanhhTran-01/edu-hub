package com.eduhub.edu_hub.shared.systemconfig.application.service;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.request.UpdateSystemConfigsRequest;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.response.SystemConfigResponse;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.response.SystemConfigSnapshotResponse;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import com.eduhub.edu_hub.shared.systemconfig.domain.model.SystemConfig;
import com.eduhub.edu_hub.shared.systemconfig.domain.policy.SystemConfigValidationPolicy;
import com.eduhub.edu_hub.shared.systemconfig.domain.repository.SystemConfigRepository;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nguồn cấu hình chung được các module gọi trực tiếp; không phụ thuộc service quản trị hoặc Identity. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemConfigService {
    private final SystemConfigRepository systemConfigRepository;
    private final Environment environment;
    private final SystemConfigValidationPolicy validationPolicy;

    public SystemConfigSnapshotResponse getSnapshot() {
        return snapshot(indexConfigurations(systemConfigRepository.findByDeletedFalseOrderByConfigKeyAsc()));
    }

    public List<SystemConfigResponse> findConfigurations(SystemConfigKey.Group group) {
        var configurations = indexConfigurations(systemConfigRepository.findByDeletedFalseOrderByConfigKeyAsc());
        return responses(configurations, snapshot(configurations), group);
    }

    @Transactional
    public List<SystemConfigResponse> updateConfigurations(UpdateSystemConfigsRequest request, Long actorUserId) {
        if (actorUserId == null
                || request == null
                || request.configurations() == null
                || request.configurations().isEmpty()
                || request.configurations().size() > SystemConfigKey.values().length)
            throw new SystemConfigException(ErrorCode.VALIDATION_ERROR);

        // Khóa cùng thứ tự toàn bộ danh mục để hai lần sửa các khóa liên quan không vượt validation chéo.
        var configurations = indexConfigurations(systemConfigRepository.findConfigurationsForUpdate());
        var proposedValues = resolveValues(configurations);
        var updatedKeys = new HashSet<SystemConfigKey>();
        for (var change : request.configurations()) {
            if (change == null
                    || change.key() == null
                    || change.value() == null
                    || change.value().length() > 4096
                    || change.expectedRevision() == null
                    || change.expectedRevision() < 0
                    || !updatedKeys.add(change.key())) throw new SystemConfigException(ErrorCode.VALIDATION_ERROR);
            if (configurations.get(change.key()).getRevision() != change.expectedRevision())
                throw new SystemConfigException(
                        ErrorCode.SYSTEM_CONFIG_CHANGED,
                        List.of(new FieldErrorDetail(
                                change.key().propertyName(), "Cấu hình đã thay đổi; hãy tải lại")));
            proposedValues.put(change.key(), change.value());
        }

        var normalizedValues = validationPolicy.normalizeAndValidate(proposedValues);
        // Chỉ cập nhật entity đang quản lý sau khi toàn bộ lô hợp lệ; lỗi phải rollback cả lô.
        for (var change : request.configurations()) {
            var configuration = configurations.get(change.key());
            String configuredValue = normalizedValues.get(change.key());
            if (configuredValue == null) throw SystemConfigException.unavailable(change.key());
            if (!Objects.equals(configuration.getConfigValue(), configuredValue)) {
                configuration.setConfigValue(configuredValue);
                configuration.setUpdatedBy(actorUserId.toString());
            }
        }
        systemConfigRepository.flush();
        // TODO(AUDIT_LOG): Ghi người sửa, các khóa và giá trị trước/sau đúng sau commit khi có hạ tầng audit.
        return responses(configurations, new SystemConfigSnapshotResponse(normalizedValues), null);
    }

    private Map<SystemConfigKey, SystemConfig> indexConfigurations(List<SystemConfig> configurations) {
        Map<SystemConfigKey, SystemConfig> configurationsByKey = new EnumMap<>(SystemConfigKey.class);
        for (var configuration : configurations) {
            try {
                var key = SystemConfigKey.fromPropertyName(configuration.getConfigKey());
                if (configurationsByKey.put(key, configuration) != null) throw SystemConfigException.unavailable(key);
            } catch (IllegalArgumentException exception) {
                throw new SystemConfigException(ErrorCode.SYSTEM_CONFIG_UNAVAILABLE);
            }
        }
        for (var key : SystemConfigKey.values())
            if (!configurationsByKey.containsKey(key)) throw SystemConfigException.unavailable(key);
        return configurationsByKey;
    }

    private Map<SystemConfigKey, String> resolveValues(Map<SystemConfigKey, SystemConfig> configurations) {
        Map<SystemConfigKey, String> effectiveValues = new EnumMap<>(SystemConfigKey.class);
        for (var key : SystemConfigKey.values()) {
            String configuredValue = configurations.get(key).getConfigValue();
            if (configuredValue == null) {
                configuredValue = environment.getProperty(key.propertyName());
                if (configuredValue == null && key == SystemConfigKey.ADMISSION_NOTIFICATIONS_ENABLED)
                    configuredValue = environment.getProperty("admission.notifications-enabled");
                if (configuredValue == null) configuredValue = key.getDefaultValue();
                if (key.getDefaultValue() == null && configuredValue != null && configuredValue.isBlank())
                    configuredValue = null;
            }
            effectiveValues.put(key, configuredValue);
        }
        return effectiveValues;
    }

    private SystemConfigSnapshotResponse snapshot(Map<SystemConfigKey, SystemConfig> configurations) {
        try {
            return new SystemConfigSnapshotResponse(
                    validationPolicy.normalizeAndValidate(resolveValues(configurations)));
        } catch (SystemConfigException exception) {
            throw new SystemConfigException(ErrorCode.SYSTEM_CONFIG_UNAVAILABLE, exception.getFieldErrors());
        }
    }

    private List<SystemConfigResponse> responses(
            Map<SystemConfigKey, SystemConfig> configurations,
            SystemConfigSnapshotResponse snapshot,
            SystemConfigKey.Group group) {
        return configurations.entrySet().stream()
                .filter(entry -> group == null || entry.getKey().getGroup() == group)
                .map(entry -> new SystemConfigResponse(
                        entry.getKey(),
                        entry.getKey().getGroup(),
                        entry.getKey().getDataType(),
                        entry.getKey().getLabel(),
                        snapshot.values().get(entry.getKey()),
                        entry.getValue().getConfigValue() != null,
                        entry.getValue().getRevision(),
                        entry.getValue().getUpdatedAt(),
                        entry.getValue().getUpdatedBy()))
                .toList();
    }
}
