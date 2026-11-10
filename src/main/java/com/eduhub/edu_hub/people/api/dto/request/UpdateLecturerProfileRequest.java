package com.eduhub.edu_hub.people.api.dto.request;

import com.eduhub.edu_hub.people.application.exception.LecturerProfileException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** PUT cập nhật từng phần: bỏ field/null giữ nguyên, danh sách môn có giá trị thay thế toàn bộ tập hiện tại. */
@Data
public class UpdateLecturerProfileRequest {
    @Size(max = 255, message = "Họ tên tối đa 255 ký tự")
    private String fullName;

    @Past(message = "Ngày sinh phải là ngày trong quá khứ")
    private LocalDate dateOfBirth;

    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @Size(max = 255, message = "Số điện thoại tối đa 255 ký tự")
    private String phone;

    private String citizenId;

    @Size(min = 1, message = "Phải chọn ít nhất một môn có thể dạy")
    private List<@NotNull(message = "ID môn không được null") @Positive(message = "ID môn phải là số dương") Long>
            teachingSubjectIds;

    @Size(max = 500, message = "Lý do cập nhật tối đa 500 ký tự")
    private String reason;

    public void setEmail(String email) {
        // Giữ khác biệt giữa null/bỏ field (không sửa) và chuỗi trắng (xóa email tại User), giống PUT sinh viên.
        if (email == null) {
            this.email = null;
            return;
        }
        String normalizedEmail = TextsNormalizer.normalizeEmail(email);
        this.email = normalizedEmail == null ? "" : normalizedEmail;
    }

    @JsonAnySetter
    public void rejectUnsupportedField(String field, Object fieldValue) {
        // Không bỏ qua key bị cấm dù giá trị null hoặc request đồng thời có các field được sửa.
        throw new LecturerProfileException(
                ErrorCode.VALIDATION_ERROR, field, "Trường không được phép cập nhật qua hồ sơ giảng viên: " + field);
    }
}
