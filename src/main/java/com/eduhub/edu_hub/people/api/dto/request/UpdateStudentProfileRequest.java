package com.eduhub.edu_hub.people.api.dto.request;

import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class UpdateStudentProfileRequest {

    // TODO: cần chỉnh thêm những trường không cho admin sửa (chỉ có cá nhân sinh viên mới sửa)

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

    @DecimalMin(value = "0.0", message = "GPA mục tiêu phải từ 0 đến 4")
    @DecimalMax(value = "4.0", message = "GPA mục tiêu phải từ 0 đến 4")
    @Digits(integer = 1, fraction = 2, message = "GPA mục tiêu tối đa 2 chữ số thập phân")
    private BigDecimal targetGpa;

    // TODO(ACADEMIC_WARNING_EVALUATION): Khi có dữ liệu học tập và quy chế đã chốt, tích hợp kết quả
    //  đánh giá cảnh báo từ Academic; không nhận trạng thái/lý do cảnh báo do client nhập tay.
    //  GPA mục tiêu là mong muốn của sinh viên, không phải GPA thực tế để xác định cảnh báo học vụ.

    // TODO(STUDENT_ADMINISTRATIVE_CLASS_TRANSFER): Chuyển lớp là use-case riêng, cần kiểm tra lớp đích,
    //  ngành/khóa/hệ, sức chứa, thứ tự khóa và lưu lý do/lịch sử có hiệu lực; không gán lớp qua request này.

    // TODO(STUDENT_MAJOR_TRANSFER): Chuyển ngành cần chốt CTĐT đích, công nhận kết quả/tín chỉ và lớp mới.
    //  Hai luồng phải bảo toàn lịch sử nhập học, MSSV/username và bộ đếm đã cấp, không tự tạo lại tài khoản.

    // NOTE: Giữ tên JSON cũ; đây là lý do cập nhật hồ sơ, không phải lệnh thay đổi trạng thái sinh viên.
    @Size(max = 500, message = "Lý do cập nhật tối đa 500 ký tự")
    private String statusReason;

    public void setEmail(String email) {
        // PUT bỏ field/null giữ nguyên; giữ chuỗi rỗng làm yêu cầu xóa sau khi chuẩn hóa email trắng.
        // Không đổi chuỗi trắng thành null tại DTO vì sẽ làm mất khác biệt này trước khi service xử lý.
        if (email == null) {
            this.email = null;
            return;
        }
        String normalizedEmail = TextsNormalizer.normalizeEmail(email);
        this.email = normalizedEmail == null ? "" : normalizedEmail;
    }

    @JsonAnySetter
    public void rejectUnsupportedField(String field, Object fieldValue) {
        // Từ chối cả key có giá trị null và request trộn field hợp lệ với MSSV/lớp/trường ngoài phạm vi.
        String detail = fieldValue == null ? " (key có giá trị null cũng không được chấp nhận)" : "";
        throw new StudentProfileException(
                ErrorCode.VALIDATION_ERROR,
                field,
                "Trường không được phép cập nhật qua hồ sơ sinh viên: " + field + detail);
    }
}
