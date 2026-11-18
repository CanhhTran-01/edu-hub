package com.eduhub.edu_hub.enrollment.api.dto.response;

import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassGradingResponse;
import java.util.List;

/** Chỉ các lượt REGISTERED còn hiệu lực của lớp được nhập điểm. */
public record ProcessScoreRosterResponse(
        ModuleClassGradingResponse moduleClass, List<ModuleClassStudentResponse> students) {}
