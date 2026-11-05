package com.eduhub.edu_hub.scheduling.api.dto.request;

import com.eduhub.edu_hub.scheduling.domain.enums.SemesterStatus;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SemesterSearchRequest {
    private String keyword;
    private SemesterStatus status;

    @Min(value = 0, message = "Số trang phải từ 0 trở lên")
    private int page = 0;

    @Min(value = 1, message = "Kích thước trang phải lớn hơn 0")
    private int size = 20;
}
