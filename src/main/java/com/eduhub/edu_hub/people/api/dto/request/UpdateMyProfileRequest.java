package com.eduhub.edu_hub.people.api.dto.request;

import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * DTO nhận request "sinh viên tự cập nhật hồ sơ của mình" (kiểu PATCH một phần).
 * Chỉ cho phép sửa đúng 3 trường: targetGpa, phone, email.
 * Mọi trường khác trong JSON đều bị từ chối (xem rejectUnknownField ở cuối).
 */
public class UpdateMyProfileRequest {
    @Getter
    @DecimalMin(value = "0.0", message = "GPA mục tiêu phải từ 0 đến 4")
    @DecimalMax(value = "4.0", message = "GPA mục tiêu phải từ 0 đến 4")
    @Digits(integer = 1, fraction = 2, message = "GPA mục tiêu tối đa 2 chữ số thập phân")
    private BigDecimal targetGpa;

    @Getter
    @Size(max = 255, message = "Số điện thoại tối đa 255 ký tự")
    private String phone;

    @Getter
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    // Cờ hiện diện chỉ do setter ghi khi đọc JSON: bỏ field giữ nguyên, gửi null xóa giá trị.
    // Không cho client ghi trực tiếp các cờ này để vượt qua danh sách trường được phép sửa.
    private boolean targetGpaPresent;
    private boolean phonePresent;
    private boolean emailPresent;

    // Jackson CHỈ gọi nhóm 3 setter này khi JSON có key "targetGpa" / "phone" / "email" (kể cả giá trị null).
    // Nên gọi được setter nghĩa là client có gửi trường này --> bật cờ.
    public void setTargetGpa(BigDecimal targetGpa) {
        this.targetGpa = targetGpa;
        targetGpaPresent = true;
    }

    public void setPhone(String phone) {
        this.phone = phone;
        phonePresent = true;
    }

    public void setEmail(String email) {
        // Chuẩn hóa trước validation, thống nhất với email khi cấp tài khoản; chuỗi trắng là chưa khai báo.
        this.email = TextsNormalizer.normalizeEmail(email);
        emailPresent = true;
    }

    // Service gọi nhóm hàm này để biết có cần cập nhật GPA hay không.
    // @JsonIgnore: để Jackson không coi là một property của JSON.
    @JsonIgnore
    public boolean hasTargetGpa() {
        return targetGpaPresent;
    }

    @JsonIgnore
    public boolean hasPhone() {
        return phonePresent;
    }

    @JsonIgnore
    public boolean hasEmail() {
        return emailPresent;
    }

    // chặn trường lạ
    // Jackson gọi hàm này cho MỌI key trong JSON mà class không có property tương ứng
    // NOTE: Jackson cần đủ hai tham số tên và giá trị; vẫn phải giữ tham số giá trị dù hàm chỉ dùng tên field.
    @JsonAnySetter
    public void rejectUnknownField(String field, Object fieldValue) {
        throw new StudentProfileException(
                ErrorCode.VALIDATION_ERROR, field, "Trường không được phép cập nhật trong hồ sơ cá nhân: " + field);
    }
}
