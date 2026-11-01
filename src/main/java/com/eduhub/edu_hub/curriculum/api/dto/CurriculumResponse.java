package com.eduhub.edu_hub.curriculum.api.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CurriculumResponse {
    private Long id;
    private String majorName;
    private String cohortCode;
    private String trainingType; // CHINH_QUY, LIEN_THONG, VAN_BANG_2
    private int totalCredits;
    private List<CurriculumSubjectResponse> subjects;
}
