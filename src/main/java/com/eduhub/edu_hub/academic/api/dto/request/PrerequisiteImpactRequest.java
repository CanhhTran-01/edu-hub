package com.eduhub.edu_hub.academic.api.dto.request;

import com.eduhub.edu_hub.academic.domain.enums.PrerequisiteAnalysisScope;
import java.util.List;

/** Kỳ và danh tính được lấy phía server; tên/mã môn chỉ chọn phạm vi trong nguồn hiện hành. */
public record PrerequisiteImpactRequest(PrerequisiteAnalysisScope scope, List<String> courseReferences) {
    public static final int MAXIMUM_COURSE_REFERENCES = 100;
}
