package com.eduhub.edu_hub.admission.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Tra cứu theo ngành–khóa; plan=null là chưa lập phương án, GET không tự tạo dữ liệu. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdmissionPlanLookupResponse(Long majorId, Long cohortId, AdmissionPlanResponse plan) {}
