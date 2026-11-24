package com.eduhub.edu_hub.shared.systemconfig.domain.policy;

import static com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.*;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

/** Kiểm tra kiểu và quan hệ giữa các tham số từ bản chụp đã tải; không truy vấn dữ liệu. */
@Component
@RequiredArgsConstructor
public class SystemConfigValidationPolicy {
    private final Validator validator;

    public Map<SystemConfigKey, String> normalizeAndValidate(Map<SystemConfigKey, String> proposedValues) {
        // Chỉ kiểm tra quan hệ sau khi mọi giá trị đã qua kiểm tra kiểu dữ liệu.
        var normalizedValues = normalizeConfigurationValues(proposedValues);
        List<FieldErrorDetail> errors = new ArrayList<>();
        validateNumericRanges(normalizedValues, errors);
        validateRelatedLimits(normalizedValues, errors);
        validateFormatsAndMailSettings(normalizedValues, errors);
        if (!errors.isEmpty()) throw new SystemConfigException(ErrorCode.VALIDATION_ERROR, errors);
        return Map.copyOf(normalizedValues);
    }

    private void requireRange(
            Map<SystemConfigKey, String> values,
            SystemConfigKey key,
            int minimum,
            int maximum,
            List<FieldErrorDetail> errors) {
        if (values.containsKey(key) && (integer(values, key) < minimum || integer(values, key) > maximum))
            errors.add(error(key, "Giá trị phải từ " + minimum + " đến " + maximum));
    }

    private void requireOrdered(
            Map<SystemConfigKey, String> values,
            SystemConfigKey minimumKey,
            SystemConfigKey maximumKey,
            List<FieldErrorDetail> errors) {
        if (values.containsKey(minimumKey)
                && values.containsKey(maximumKey)
                && integer(values, minimumKey) > integer(values, maximumKey))
            errors.add(error(minimumKey, "Giá trị không được lớn hơn " + maximumKey.propertyName()));
    }

    private int integer(Map<SystemConfigKey, String> values, SystemConfigKey key) {
        return Integer.parseInt(values.get(key));
    }

    private void validateCodeFormat(
            String codeFormat, SystemConfigKey key, List<String> tokens, List<FieldErrorDetail> errors) {
        String remainingFormat = codeFormat;
        for (String token : tokens) {
            if (!remainingFormat.contains(token)
                    || remainingFormat.indexOf(token) != remainingFormat.lastIndexOf(token)) {
                errors.add(error(key, "Mỗi thành phần định danh phải xuất hiện đúng một lần"));
                return;
            }
            remainingFormat = remainingFormat.replace(token, "");
        }
        if (!remainingFormat.matches("[A-Za-z0-9_-]*"))
            errors.add(error(key, "Định dạng chứa thành phần hoặc ký tự phân cách không được hỗ trợ"));
    }

    private FieldErrorDetail error(SystemConfigKey key, String message) {
        return new FieldErrorDetail(key.propertyName(), message);
    }

    private record SupportEmail(@Email String email) {}

    private Map<SystemConfigKey, String> normalizeConfigurationValues(Map<SystemConfigKey, String> proposedValues) {
        Map<SystemConfigKey, String> normalizedValues = new EnumMap<>(SystemConfigKey.class);
        List<FieldErrorDetail> errors = new ArrayList<>();
        for (SystemConfigKey key : SystemConfigKey.values()) {
            String proposedValue = proposedValues.get(key);
            // Chưa có quy chế tín chỉ thì để chưa cấu hình; không tự suy ra ngưỡng.
            if (proposedValue == null) {
                if (key.getDefaultValue() != null) errors.add(error(key, "Tham số bắt buộc phải có giá trị"));
                continue;
            }
            String normalizedValue =
                    key == STUDENT_CODE_FORMAT || key == LECTURER_CODE_FORMAT ? proposedValue : proposedValue.strip();
            try {
                switch (key.getDataType()) {
                    case INTEGER -> normalizedValue = Integer.toString(Integer.parseInt(normalizedValue));
                    case BOOLEAN -> {
                        if (!normalizedValue.equals("true") && !normalizedValue.equals("false"))
                            throw new IllegalArgumentException();
                    }
                    case DURATION -> {
                        Duration duration = DurationStyle.detectAndParse(normalizedValue);
                        if (duration.isNegative() || duration.toSeconds() < 1) throw new IllegalArgumentException();
                        duration.toMillis();
                        normalizedValue = duration.toString();
                    }
                    case STRING -> {}
                    case STRING_MAP -> normalizedValue =
                            CodePrefixMappingPolicy.normalizeMappings(normalizedValue, key);
                }
                normalizedValues.put(key, normalizedValue);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                errors.add(error(key, "Giá trị không hợp lệ với kiểu " + key.getDataType()));
            }
        }
        if (!errors.isEmpty()) throw new SystemConfigException(ErrorCode.VALIDATION_ERROR, errors);

        return normalizedValues;
    }

    private void validateNumericRanges(Map<SystemConfigKey, String> normalizedValues, List<FieldErrorDetail> errors) {
        requireRange(normalizedValues, ADMISSION_MINIMUM_CAPACITY, 1, 99, errors);
        requireRange(normalizedValues, ADMISSION_MAXIMUM_CAPACITY, 1, 99, errors);
        requireRange(normalizedValues, ADMISSION_RESERVE_CAPACITY, 0, 98, errors);
        requireRange(normalizedValues, ADMISSION_MAXIMUM_SAME_NAME, 1, 99, errors);
        requireRange(normalizedValues, ENROLLMENT_MINIMUM_CREDITS, 0, Integer.MAX_VALUE, errors);
        requireRange(normalizedValues, ENROLLMENT_MAXIMUM_CREDITS, 1, Integer.MAX_VALUE, errors);
        requireRange(normalizedValues, MODULE_CLASS_DEFAULT_CAPACITY, 1, Integer.MAX_VALUE, errors);
        requireRange(normalizedValues, MODULE_CLASS_MAXIMUM_CAPACITY, 1, Integer.MAX_VALUE, errors);
        requireRange(normalizedValues, PASSWORD_MINIMUM_LENGTH, 8, 72, errors);
        requireRange(normalizedValues, AUTHENTICATION_MAXIMUM_FAILED_LOGINS, 1, Integer.MAX_VALUE, errors);
    }

    private void validateRelatedLimits(Map<SystemConfigKey, String> normalizedValues, List<FieldErrorDetail> errors) {
        requireOrdered(normalizedValues, ADMISSION_MINIMUM_CAPACITY, ADMISSION_MAXIMUM_CAPACITY, errors);
        if (integer(normalizedValues, ADMISSION_MAXIMUM_CAPACITY)
                        + (long) integer(normalizedValues, ADMISSION_RESERVE_CAPACITY)
                > 99) errors.add(error(ADMISSION_RESERVE_CAPACITY, "Tổng sĩ số tối đa và dự phòng không được vượt 99"));

        boolean hasMinimumCredits = normalizedValues.containsKey(ENROLLMENT_MINIMUM_CREDITS);
        boolean hasMaximumCredits = normalizedValues.containsKey(ENROLLMENT_MAXIMUM_CREDITS);
        if (hasMinimumCredits != hasMaximumCredits)
            errors.add(error(
                    hasMinimumCredits ? ENROLLMENT_MAXIMUM_CREDITS : ENROLLMENT_MINIMUM_CREDITS,
                    "Cần thiết lập đầy đủ cả hai giới hạn tín chỉ"));
        requireOrdered(normalizedValues, ENROLLMENT_MINIMUM_CREDITS, ENROLLMENT_MAXIMUM_CREDITS, errors);
        requireOrdered(normalizedValues, MODULE_CLASS_DEFAULT_CAPACITY, MODULE_CLASS_MAXIMUM_CAPACITY, errors);
    }

    private void validateFormatsAndMailSettings(
            Map<SystemConfigKey, String> normalizedValues, List<FieldErrorDetail> errors) {
        validateCodeFormat(
                normalizedValues.get(STUDENT_CODE_FORMAT),
                STUDENT_CODE_FORMAT,
                List.of("[MaNganh]", "[SttKhoa]", "[SttLop]", "[SttSVTrongLop]"),
                errors);
        validateCodeFormat(
                normalizedValues.get(LECTURER_CODE_FORMAT),
                LECTURER_CODE_FORMAT,
                List.of("[MaKhoa]", "[NamTuyenDung]", "[SttGV]"),
                errors);
        if (normalizedValues.get(MAIL_BRAND_NAME).isBlank())
            errors.add(error(MAIL_BRAND_NAME, "Tên thương hiệu không được để trống"));
        if (!normalizedValues.get(MAIL_LOGIN_URL).matches("^$|https?://\\S+"))
            errors.add(error(MAIL_LOGIN_URL, "URL phải bắt đầu bằng http:// hoặc https:// và không có khoảng trắng"));
        if (!validator
                .validate(new SupportEmail(normalizedValues.get(MAIL_SUPPORT_EMAIL)))
                .isEmpty()) errors.add(error(MAIL_SUPPORT_EMAIL, "Email hỗ trợ không hợp lệ"));
    }
}
