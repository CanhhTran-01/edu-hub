package com.eduhub.edu_hub.people.application.facade.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdmissionStudentInfo {
    Long id;
    Long userId;
    String studentCode;
    String citizenId;
    String fullName;
    boolean deleted;
}
