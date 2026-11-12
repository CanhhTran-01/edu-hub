package com.eduhub.edu_hub.curriculum.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSubjectRequest {
    @NotBlank(message = "Mã học phần không được để trống")
    private String subjectCode;

    @NotBlank(message = "Tên học phần không được để trống")
    private String subjectName;

    @NotNull(message = "Số tín chỉ không được để trống")
    @Min(value = 1, message = "Số tín chỉ phải từ 1 đến 5")
    @Max(value = 5, message = "Số tín chỉ phải từ 1 đến 5")
    private Integer credits;

    @NotNull(message = "Tổng số tiết không được để trống")
    @tools.jackson.databind.annotation.JsonDeserialize(
            using = com.eduhub.edu_hub.curriculum.api.serialization.TeachingPeriodsDeserializer.class)
    @Min(value = 1, message = "Tổng số tiết phải là số nguyên dương")
    private Integer totalTeachingPeriods;

    private String description;
}
