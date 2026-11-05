package com.eduhub.edu_hub.scheduling.domain.model;

import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "rooms",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_rooms_code", columnNames = "room_code"),
            @UniqueConstraint(
                    name = "uk_rooms_location",
                    columnNames = {"campus", "building_code", "room_number"})
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room extends BaseEntity {
    @Column(name = "room_code", nullable = false, length = 50)
    private String roomCode;

    @Column(name = "room_name", nullable = false)
    private String roomName;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String campus = "";

    @Column(name = "building_code", nullable = false, length = 50)
    private String buildingCode;

    @Column(name = "room_number", nullable = false, length = 50)
    private String roomNumber;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private RoomStatus status = RoomStatus.ACTIVE;
}
