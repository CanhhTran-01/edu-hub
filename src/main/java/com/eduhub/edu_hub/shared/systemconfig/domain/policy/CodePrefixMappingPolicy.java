package com.eduhub.edu_hub.shared.systemconfig.domain.policy;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Chuẩn hóa khóa viết tắt và tra tiền tố từ mapping đã cung cấp; không tự suy ra tiền tố thay thế. */
public final class CodePrefixMappingPolicy {
    private static final int MAXIMUM_PREFIX_LENGTH = 16;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private CodePrefixMappingPolicy() {}

    public static String lookupKeyFromName(String name) {
        if (name == null) throw invalidName();
        String normalizedName = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toUpperCase(Locale.ROOT);
        StringBuilder lookupKey = new StringBuilder();
        for (String word : normalizedName.split("[^A-Z]+")) {
            if (!word.isEmpty()) lookupKey.append(word.charAt(0));
        }
        if (lookupKey.isEmpty()) throw invalidName();
        return lookupKey.toString();
    }

    public static Map<String, String> parseMappings(String json, SystemConfigKey configurationKey) {
        try {
            var mappingObject = JSON.readTree(json);
            if (mappingObject == null || !mappingObject.isObject()) throw new IllegalArgumentException();
            Map<String, String> prefixesByKey = new TreeMap<>();
            var reservedPrefixes = new HashSet<String>();
            for (var entry : mappingObject.properties()) {
                if (!entry.getValue().isString()) throw new IllegalArgumentException();
                String lookupKey = entry.getKey().strip().toUpperCase(Locale.ROOT);
                String prefix = entry.getValue().asString().strip().toUpperCase(Locale.ROOT);
                boolean isTrainingType = configurationKey == SystemConfigKey.TRAINING_TYPE_CODE_PREFIXES;
                if (!lookupKey.matches(isTrainingType ? "[A-Z][A-Z0-9_]*" : "[A-Z]+")
                        || !prefix.matches(isTrainingType ? "[A-Z][A-Z0-9]*" : "[A-Z]+")
                        || prefix.length() > MAXIMUM_PREFIX_LENGTH
                        || prefixesByKey.putIfAbsent(lookupKey, prefix) != null
                        || !reservedPrefixes.add(prefix)) throw new IllegalArgumentException();
            }
            return Map.copyOf(prefixesByKey);
        } catch (RuntimeException exception) {
            throw new SystemConfigException(
                    ErrorCode.VALIDATION_ERROR,
                    List.of(
                            new FieldErrorDetail(
                                    configurationKey.propertyName(),
                                    "Mapping phải là JSON object có khóa và tiền tố hợp lệ; tiền tố tối đa 16 ký tự, không trùng khóa hoặc tiền tố")));
        }
    }

    public static String normalizeMappings(String json, SystemConfigKey configurationKey) {
        return JSON.writeValueAsString(new TreeMap<>(parseMappings(json, configurationKey)));
    }

    public static String requirePrefix(
            Map<String, String> prefixesByKey, SystemConfigKey configurationKey, String lookupKey) {
        String prefix = prefixesByKey.get(lookupKey);
        if (prefix == null) throw missingMapping(configurationKey, lookupKey);
        // Policy có thể được gọi độc lập từ luồng tạo CTĐT trong tương lai, không chỉ qua parser cấu hình.
        String prefixPattern =
                configurationKey == SystemConfigKey.TRAINING_TYPE_CODE_PREFIXES ? "[A-Z][A-Z0-9]*" : "[A-Z]+";
        if (prefix.length() > MAXIMUM_PREFIX_LENGTH || !prefix.matches(prefixPattern))
            throw new SystemConfigException(
                    ErrorCode.VALIDATION_ERROR,
                    List.of(new FieldErrorDetail(configurationKey.propertyName(), "Tiền tố sinh mã không hợp lệ")));
        return prefix;
    }

    public static SystemConfigException missingMapping(SystemConfigKey configurationKey, String lookupKey) {
        return new SystemConfigException(
                ErrorCode.CODE_PREFIX_MAPPING_MISSING,
                List.of(new FieldErrorDetail(
                        configurationKey.propertyName(),
                        "Thiếu mapping cho khóa " + lookupKey + ". SYSTEM_ADMIN cần vào Cấu hình hệ thống → "
                                + configurationKey.getLabel() + ", thêm khóa " + lookupKey
                                + " và tiền tố mong muốn, lưu cấu hình rồi thử lại.")));
    }

    public static void requireUnambiguousName(
            String name, Collection<String> catalogNames, SystemConfigKey configurationKey) {
        // TODO(CATALOG_MAPPING_KEY_OVERRIDE): Chưa có luồng quản trị khóa riêng khi hai tên viết tắt giống nhau.
        // Khi bổ sung CRUD danh mục cần chốt quy ước phân biệt; hiện chặn cấp mã thay vì dùng chung tiền tố.
        String lookupKey = lookupKeyFromName(name);
        long matchingNames = catalogNames.stream()
                .filter(catalogName -> lookupKeyFromName(catalogName).equals(lookupKey))
                .count();
        if (matchingNames > 1)
            throw new SystemConfigException(
                    ErrorCode.CODE_PREFIX_MAPPING_AMBIGUOUS,
                    List.of(
                            new FieldErrorDetail(
                                    configurationKey.propertyName(),
                                    "Nhiều tên danh mục cùng tạo khóa " + lookupKey
                                            + "; cần đối soát khóa định danh riêng trước khi cấp mã để tránh dùng chung tiền tố.")));
    }

    private static SystemConfigException invalidName() {
        return new SystemConfigException(
                ErrorCode.VALIDATION_ERROR,
                List.of(new FieldErrorDetail("name", "Tên cần có chữ cái để tạo khóa viết tắt tra mapping")));
    }
}
