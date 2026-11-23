package com.eduhub.edu_hub.admission.api.dto.request;

import lombok.Builder;
import org.springframework.web.multipart.MultipartFile;

/** Sĩ số chỉ lấy từ SystemConfig khi tạo plan, không nhận giá trị ghi đè từ client. */
@Builder
public record CreateAdmissionPlanFromFileRequest(Long majorId, Long cohortId, MultipartFile file) {}
