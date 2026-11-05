package com.eduhub.edu_hub.scheduling.api.dto.response;

import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomResponse {
    private Long id;
    private String roomCode;
    private String roomName;
    private String campus;
    private String buildingCode;
    private String roomNumber;
    private int capacity;
    private RoomStatus status;
}
