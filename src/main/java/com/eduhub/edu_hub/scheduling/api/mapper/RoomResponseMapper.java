package com.eduhub.edu_hub.scheduling.api.mapper;

import com.eduhub.edu_hub.scheduling.api.dto.response.RoomResponse;
import com.eduhub.edu_hub.scheduling.domain.model.Room;

public final class RoomResponseMapper {
    private RoomResponseMapper() {}

    public static RoomResponse toResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .campus(room.getCampus())
                .buildingCode(room.getBuildingCode())
                .roomNumber(room.getRoomNumber())
                .capacity(room.getCapacity())
                .status(room.getStatus())
                .build();
    }
}
