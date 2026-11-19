package com.eduhub.edu_hub.shared.systemconfig.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Danh mục tham số được quản trị; không chứa secret hoặc cho phép tạo khóa tùy ý. */
@Getter
@RequiredArgsConstructor
public enum SystemConfigKey {
    ADMISSION_MINIMUM_CAPACITY(
            "admission.min-capacity", Group.ADMISSION, DataType.INTEGER, "Sĩ số nhập học tối thiểu", "40"),
    ADMISSION_MAXIMUM_CAPACITY(
            "admission.max-capacity", Group.ADMISSION, DataType.INTEGER, "Sĩ số nhập học tối đa", "50"),
    ADMISSION_RESERVE_CAPACITY(
            "admission.reserve-capacity", Group.ADMISSION, DataType.INTEGER, "Số chỗ dự phòng nhập học", "15"),
    ADMISSION_MAXIMUM_SAME_NAME(
            "admission.maximum-same-name-per-class",
            Group.ADMISSION,
            DataType.INTEGER,
            "Mục tiêu số sinh viên cùng họ tên trong lớp",
            "3"),
    STUDENT_CODE_FORMAT(
            "admission.student-code-format",
            Group.PEOPLE,
            DataType.STRING,
            "Định dạng MSSV cấp mới",
            "[MaNganh][SttKhoa][SttLop][SttSVTrongLop]"),
    LECTURER_CODE_FORMAT(
            "people.lecturer-code-format",
            Group.PEOPLE,
            DataType.STRING,
            "Định dạng mã giảng viên cấp mới",
            "[MaKhoa][NamTuyenDung][SttGV]"),
    ENROLLMENT_MINIMUM_CREDITS(
            "enrollment.registration.minimum-credits",
            Group.ENROLLMENT,
            DataType.INTEGER,
            "Tín chỉ đăng ký tối thiểu để cảnh báo",
            null),
    ENROLLMENT_MAXIMUM_CREDITS(
            "enrollment.registration.maximum-credits",
            Group.ENROLLMENT,
            DataType.INTEGER,
            "Tín chỉ đăng ký tối đa",
            null),
    MODULE_CLASS_DEFAULT_CAPACITY(
            "scheduling.module-class.default-capacity",
            Group.SCHEDULING,
            DataType.INTEGER,
            "Sĩ số mặc định của lớp học phần mới",
            "80"),
    MODULE_CLASS_MAXIMUM_CAPACITY(
            "scheduling.module-class.maximum-capacity",
            Group.SCHEDULING,
            DataType.INTEGER,
            "Trần sức chứa khi tạo hoặc sửa lớp học phần",
            "80"),
    PASSWORD_MINIMUM_LENGTH(
            "identity.password.minimum-length",
            Group.IDENTITY,
            DataType.INTEGER,
            "Độ dài tối thiểu của mật khẩu tự đặt",
            "8"),
    AUTHENTICATION_MAXIMUM_FAILED_LOGINS(
            "identity.authentication.max-failed-logins",
            Group.IDENTITY,
            DataType.INTEGER,
            "Số lần sai mật khẩu trước khi khóa tạm",
            "5"),
    AUTHENTICATION_TEMPORARY_LOCK_DURATION(
            "identity.authentication.temporary-lock-duration",
            Group.IDENTITY,
            DataType.DURATION,
            "Thời gian khóa tạm tài khoản",
            "15m"),
    ACCESS_TOKEN_LIFETIME(
            "jwt.access-token-lifetime", Group.IDENTITY, DataType.DURATION, "Thời hạn access token cấp mới", "15m"),
    REFRESH_TOKEN_LIFETIME(
            "jwt.refresh-token-lifetime", Group.IDENTITY, DataType.DURATION, "Thời hạn phiên đăng nhập cấp mới", "7d"),
    ADMISSION_NOTIFICATIONS_ENABLED(
            "notification.admission-enabled",
            Group.NOTIFICATION,
            DataType.BOOLEAN,
            "Bật gửi thông báo từ hàng đợi nhập học",
            "true"),
    MAIL_BRAND_NAME(
            "notification.mail.brand-name",
            Group.NOTIFICATION,
            DataType.STRING,
            "Tên thương hiệu trong email",
            "EduHub"),
    MAIL_LOGIN_URL(
            "notification.mail.login-url", Group.NOTIFICATION, DataType.STRING, "Đường dẫn đăng nhập trong email", ""),
    MAIL_SUPPORT_EMAIL(
            "notification.mail.support-email",
            Group.NOTIFICATION,
            DataType.STRING,
            "Email hỗ trợ trong email thông báo",
            "");

    private final String propertyName;
    private final Group group;
    private final DataType dataType;
    private final String label;
    private final String defaultValue;

    @JsonValue
    public String propertyName() {
        return propertyName;
    }

    @JsonCreator
    public static SystemConfigKey fromPropertyName(String propertyName) {
        return Arrays.stream(values())
                .filter(key -> key.propertyName.equals(propertyName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Khóa cấu hình không được hỗ trợ"));
    }

    public enum Group {
        ADMISSION,
        PEOPLE,
        ENROLLMENT,
        SCHEDULING,
        IDENTITY,
        NOTIFICATION
    }

    public enum DataType {
        INTEGER,
        BOOLEAN,
        DURATION,
        STRING
    }
}
