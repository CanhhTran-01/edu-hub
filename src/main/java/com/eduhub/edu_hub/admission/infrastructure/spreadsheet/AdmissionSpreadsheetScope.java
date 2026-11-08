package com.eduhub.edu_hub.admission.infrastructure.spreadsheet;

import lombok.Builder;

/** Phạm vi danh mục đã chọn dùng để tạo mẫu và đối chiếu file, không cho file đổi scope của plan. */
@Builder
public record AdmissionSpreadsheetScope(String majorCode, String majorName, String cohortCode) {}
