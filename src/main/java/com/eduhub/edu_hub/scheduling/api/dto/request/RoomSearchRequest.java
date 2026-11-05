package com.eduhub.edu_hub.scheduling.api.dto.request;

import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RoomSearchRequest {
    private String keyword;
    private String buildingCode;
    private String campus;
    private RoomStatus status;

    @Min(value = 1, message = "Sức chứa tối thiểu phải lớn hơn 0")
    private Integer minCapacity;

    @Min(value = 0, message = "Số trang phải từ 0 trở lên")
    private int page = 0;

    @Min(value = 1, message = "Kích thước trang phải lớn hơn 0")
    private int size = 20;
}
