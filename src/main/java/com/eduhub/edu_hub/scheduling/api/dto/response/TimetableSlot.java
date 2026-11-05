package com.eduhub.edu_hub.scheduling.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimetableSlot {
    private int dayOfWeek;
    private int lessonStart;
    private int lessonEnd;
    private String subjectName;
    private String moduleClassCode;
    private String roomCode;
    private String lecturerName;
}
