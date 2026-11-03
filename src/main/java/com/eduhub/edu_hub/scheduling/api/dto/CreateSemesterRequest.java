package com.eduhub.edu_hub.scheduling.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateSemesterRequest {
    @NotBlank(message = "Tên kỳ học không được để trống")
    private String semesterName;

    @NotNull(message = "Ngày bắt đầu kỳ học không được để trống")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc kỳ học không được để trống")
    private LocalDate endDate;

    @NotNull(message = "Ngày mở đăng ký không được để trống")
    private LocalDate registrationStart;

    @NotNull(message = "Ngày đóng đăng ký không được để trống")
    private LocalDate registrationEnd;
}
