package com.eduhub.edu_hub.scheduling.api.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClassScheduleResponse {
    private Long id;
    private Long moduleClassId;
    private String roomCode;
    private String buildingCode;
    private String roomNumber;
    private Integer dayOfWeek; // 2 = Thứ 2 ... 8 = Chủ nhật
    private Integer lessonOrderStart; // tiết bắt đầu
    private Integer lessonOrderEnd; // tiết kết thúc
    private LocalDate startDate; // tuần bắt đầu
    private LocalDate endDate; // tuần kết thúc
}
