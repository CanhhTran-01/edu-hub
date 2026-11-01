package com.eduhub.edu_hub.curriculum.api.dto;

import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubjectResponse {
    private Long id;
    private String subjectCode;
    private String subjectName;
    private int credits;
    private SubjectStatus status;
    private List<SubjectSimpleResponse> prerequisites;
    private List<SubjectSimpleResponse> requiredBy; // học phần lấy cái này làm tiên quyết
}
