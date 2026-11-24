package com.eduhub.edu_hub.people.api.dto.request;

import com.eduhub.edu_hub.people.application.exception.CohortException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Chỉ nhận niên khóa; mã khóa và số thứ tự được backend cấp trong transaction tạo mới. */
@Getter
@NoArgsConstructor
public class CreateCohortRequest {
    @NotNull(message = "Năm bắt đầu không được để trống")
    @Min(value = 1, message = "Năm bắt đầu phải từ 1 đến 9998")
    @Max(value = 9998, message = "Năm bắt đầu phải từ 1 đến 9998")
    private Integer startYear;

    @NotNull(message = "Năm kết thúc không được để trống")
    @Min(value = 2, message = "Năm kết thúc phải từ 2 đến 9999")
    @Max(value = 9999, message = "Năm kết thúc phải từ 2 đến 9999")
    private Integer endYear;

    // Nhận token JSON gốc để không ép chuỗi hoặc số thập phân thành năm nguyên.
    public void setStartYear(Object startYear) {
        this.startYear = readIntegerYear("startYear", startYear);
    }

    public void setEndYear(Object endYear) {
        this.endYear = readIntegerYear("endYear", endYear);
    }

    private Integer readIntegerYear(String field, Object suppliedYear) {
        if (suppliedYear == null) return null;
        if (!(suppliedYear instanceof Integer integerYear))
            throw new CohortException(ErrorCode.VALIDATION_ERROR, field, "Năm phải là số nguyên JSON");
        return integerYear;
    }

    @JsonAnySetter
    public void rejectUnsupportedField(String field, Object ignoredFieldValue) {
        throw new CohortException(ErrorCode.VALIDATION_ERROR, field, "Trường không được phép khi tạo khóa: " + field);
    }
}
