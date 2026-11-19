package com.eduhub.edu_hub.enrollment.infrastructure.config;

import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Mốc khóa mới có lịch sử được quản lý từ đầu; giới hạn tín chỉ đọc từ SystemConfig. */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "enrollment.registration")
public class EnrollmentRegistrationSettings {
    private Integer firstSupportedAdmissionYear;

    @PostConstruct
    public void validate() {
        if (firstSupportedAdmissionYear == null
                || firstSupportedAdmissionYear < 1000
                || firstSupportedAdmissionYear > 9999) {
            throw new EnrollmentException(ErrorCode.ENROLLMENT_CONFIGURATION_INVALID);
        }
    }

    public String getScopeNotice() {
        return "Số liệu chỉ bao gồm khóa tuyển sinh từ năm " + firstSupportedAdmissionYear
                + "; chưa bao gồm sinh viên chưa chuyển dữ liệu học tập.";
    }
}
