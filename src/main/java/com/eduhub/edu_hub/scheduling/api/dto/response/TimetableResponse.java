package com.eduhub.edu_hub.scheduling.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimetableResponse {
    private String weekLabel;
    private List<TimetableSlot> slots;
}
