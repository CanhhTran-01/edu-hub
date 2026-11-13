package com.eduhub.edu_hub.enrollment.infrastructure.config;

import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Giới hạn tín chỉ và mốc khóa mới có lịch sử được quản lý từ đầu trong hệ thống. */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "enrollment.registration")
public class EnrollmentRegistrationSettings {
    private Integer minimumCredits;
    private Integer maximumCredits;
    private Integer firstSupportedAdmissionYear;

    @PostConstruct
    public void validate() {
        if (minimumCredits == null
                || minimumCredits < 0
                || maximumCredits == null
                || maximumCredits < 1
                || minimumCredits > maximumCredits
                || firstSupportedAdmissionYear == null
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
