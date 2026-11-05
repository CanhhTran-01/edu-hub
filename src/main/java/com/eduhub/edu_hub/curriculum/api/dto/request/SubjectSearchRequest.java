package com.eduhub.edu_hub.curriculum.api.dto.request;

import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SubjectSearchRequest {
    private String keyword;
    private String subjectCode;
    private String subjectName;

    @Min(value = 1, message = "Số tín chỉ phải lớn hơn 0")
    private Integer credits;

    private SubjectStatus status;
    private boolean withoutPrerequisites;

    @Min(value = 0, message = "Số trang phải từ 0 trở lên")
    private int page = 0;

    @Min(value = 1, message = "Kích thước trang phải lớn hơn 0")
    private int size = 20;
}
