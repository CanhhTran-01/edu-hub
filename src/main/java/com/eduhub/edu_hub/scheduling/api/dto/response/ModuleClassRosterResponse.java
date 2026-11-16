package com.eduhub.edu_hub.scheduling.api.dto.response;

import com.eduhub.edu_hub.enrollment.api.dto.response.ModuleClassStudentResponse;
import java.util.List;

public record ModuleClassRosterResponse(ModuleClassResponse moduleClass, List<ModuleClassStudentResponse> students) {}
