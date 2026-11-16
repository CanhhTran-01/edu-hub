package com.eduhub.edu_hub.scheduling.api.dto.response;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;

/** Nguồn phân công và trạng thái lớp cho nhập điểm, không tải lịch hoặc danh mục liên module. */
public record ModuleClassGradingResponse(
        Long moduleClassId,
        String moduleClassCode,
        Long semesterId,
        String semesterCode,
        Long lecturerId,
        ModuleClassStatus status) {}
