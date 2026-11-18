package com.eduhub.edu_hub.scheduling.api.dto.response;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import lombok.Builder;
import lombok.Data;

/** Thông tin hiển thị một dòng danh sách lớp học phần; chi tiết và lịch đọc qua API theo ID lớp. */
@Data
@Builder
public class ModuleClassSimpleResponse {
    private Long id;
    private String moduleClassCode;
    private String subjectCode;
    private String subjectName;
    private int credits;
    private String semesterCode;
    private String lecturerName;
    private int maxCapacity;
    private int currentSize;
    private ModuleClassStatus status;
}
