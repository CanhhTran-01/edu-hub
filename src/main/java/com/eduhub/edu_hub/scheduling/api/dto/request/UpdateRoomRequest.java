package com.eduhub.edu_hub.scheduling.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateRoomRequest {
    @NotBlank(message = "Tên phòng không được để trống")
    @Size(max = 255, message = "Tên phòng không được vượt quá 255 ký tự")
    private String roomName;

    @NotBlank(message = "Cơ sở không được để trống")
    @Pattern(regexp = "\\s*[A-Za-z0-9]+\\s*", message = "Mã cơ sở chỉ gồm chữ cái và chữ số")
    @Size(max = 100, message = "Cơ sở không được vượt quá 100 ký tự")
    private String campus;

    @NotBlank(message = "Mã tòa nhà không được để trống")
    @Pattern(regexp = "\\s*[A-Za-z0-9]+\\s*", message = "Mã tòa nhà chỉ gồm chữ cái và chữ số")
    @Size(max = 50, message = "Mã tòa nhà không được vượt quá 50 ký tự")
    private String buildingCode;

    @NotBlank(message = "Số phòng không được để trống")
    @Pattern(regexp = "\\s*[0-9]+\\s*", message = "Số phòng chỉ gồm chữ số")
    @Size(max = 50, message = "Số phòng không được vượt quá 50 ký tự")
    private String roomNumber;

    @NotNull(message = "Sức chứa không được để trống")
    @Min(value = 1, message = "Sức chứa phải lớn hơn 0")
    private Integer capacity;
}
