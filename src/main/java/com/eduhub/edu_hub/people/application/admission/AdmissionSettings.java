package com.eduhub.edu_hub.people.application.admission;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "admission")
@Getter
@Setter
public class AdmissionSettings {
    private int minCapacity = 40;
    private int maxCapacity = 50;
    private int reserveCapacity = 15;
    private int maxImportRows = 50000;
    private int confirmationLeaseMinutes = 5;
    private String studentCodeFormat = "[MaNganh][SttKhoa][SttLop][SttSVTrongLop]";
}
