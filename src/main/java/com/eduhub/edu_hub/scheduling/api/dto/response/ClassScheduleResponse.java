package com.eduhub.edu_hub.scheduling.api.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClassScheduleResponse {
    private Long id;
    private Long moduleClassId;
    private Long roomId;
    private String roomCode;
    private String buildingCode;
    private String roomNumber;
    private Integer dayOfWeek; // 2 = Thứ 2 ... 8 = Chủ nhật
    private Integer lessonOrderStart; // tiết bắt đầu
    private Integer lessonOrderEnd; // tiết kết thúc
    private Integer shift;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private LocalDate startDate; // tuần bắt đầu
    private LocalDate endDate; // tuần kết thúc
}
