package com.eduhub.edu_hub.people.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class UpdateMyStudentProfileRequest {
    @DecimalMin(value = "0.0", message = "GPA mục tiêu phải từ 0 đến 4")
    @DecimalMax(value = "4.0", message = "GPA mục tiêu phải từ 0 đến 4")
    private BigDecimal targetGpa;
    @Size(max = 255, message = "Số điện thoại tối đa 255 ký tự")
    private String phone;
    private boolean targetGpaPresent;
    private boolean phonePresent;

    public BigDecimal getTargetGpa() { return targetGpa; }
    public String getPhone() { return phone; }
    public void setTargetGpa(BigDecimal value) { targetGpa = value; targetGpaPresent = true; }
    public void setPhone(String value) { phone = value; phonePresent = true; }
    @JsonIgnore public boolean hasTargetGpa() { return targetGpaPresent; }
    @JsonIgnore public boolean hasPhone() { return phonePresent; }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Trường không được phép cập nhật trong hồ sơ cá nhân: " + field);
    }
}
