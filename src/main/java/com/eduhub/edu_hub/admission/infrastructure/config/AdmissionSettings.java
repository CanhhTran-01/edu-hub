package com.eduhub.edu_hub.admission.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "admission")
@Getter
@Setter
public class AdmissionSettings {
    // Giới hạn tài nguyên và lease worker vẫn thuộc cấu hình triển khai.
    private int maxImportRows = 50000;
    private int confirmationLeaseMinutes = 5;
}
