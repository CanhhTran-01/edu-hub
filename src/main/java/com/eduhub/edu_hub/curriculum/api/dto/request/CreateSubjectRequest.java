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

    private String description;
}
