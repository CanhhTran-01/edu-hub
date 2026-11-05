package com.eduhub.edu_hub.scheduling.api.dto.request;

import com.eduhub.edu_hub.scheduling.api.serialization.ClassCapacityDeserializer;
import com.eduhub.edu_hub.scheduling.domain.policy.RoomPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;

@Data
public class UpdateRoomRequest {
    @NotBlank(message = "Mã phòng không được để trống")
    @Size(max = 50, message = "Mã phòng không được vượt quá 50 ký tự")
    @Pattern(regexp = "\\s*" + RoomPolicy.CODE_PATTERN + "\\s*", message = RoomPolicy.CODE_FORMAT_MESSAGE)
    private String roomCode;

    @NotBlank(message = "Tên phòng không được để trống")
    @Size(max = 255, message = "Tên phòng không được vượt quá 255 ký tự")
    private String roomName;

    @Size(max = 100, message = "Cơ sở không được vượt quá 100 ký tự")
    private String campus;

    @NotBlank(message = "Mã tòa nhà không được để trống")
    @Size(max = 50, message = "Mã tòa nhà không được vượt quá 50 ký tự")
    private String buildingCode;

    @Size(max = 50, message = "Số phòng không được vượt quá 50 ký tự")
    private String roomNumber;

    @NotNull(message = "Sức chứa không được để trống")
    @Min(value = 1, message = "Sức chứa phải lớn hơn 0")
    @JsonDeserialize(using = ClassCapacityDeserializer.class)
    private Integer capacity;
}
