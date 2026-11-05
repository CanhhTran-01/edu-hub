package com.eduhub.edu_hub.scheduling.api.dto;

import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeRoomStatusRequest {
    @NotNull(message = "Trạng thái phòng không được để trống")
    private RoomStatus status;
}
