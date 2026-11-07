package com.eduhub.edu_hub.people.application.facade.dto;

import com.eduhub.edu_hub.shared.enums.TrainingType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdmissionClassInfo {
    Long id;
    String className;
    Long majorId;
    Long cohortId;
    String majorCode;
    String majorName;
    String cohortCode;
    Integer cohortSequence;
    Integer classSequence;
    TrainingType trainingType;
    int minCapacity;
    int maxCapacity;
    int reserveCapacity;
    int lastStudentSequence;
    long currentSize;
    boolean deleted;
}
