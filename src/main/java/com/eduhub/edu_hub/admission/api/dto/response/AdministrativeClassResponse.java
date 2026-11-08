package com.eduhub.edu_hub.admission.api.dto.response;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdministrativeClassResponse {
    private Long id;
    private String className; // ví dụ: CT8A, SE17B
    private String majorCode;
    private String majorName;
    private String cohortCode; // ví dụ: K18, K19
    private TrainingType trainingType;
    private Integer currentSize; // sĩ số hiện tại (nếu cần)
    private Integer maxCapacity; // sức chứa tối đa (nếu có cấu hình)
    private Integer minCapacity;
    private Integer reserveCapacity;
    private boolean availableForAdmission;
}
