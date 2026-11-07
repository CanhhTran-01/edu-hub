package com.eduhub.edu_hub.people.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admission_applicants")
@Getter
@Setter
public class AdmissionApplicant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private AdmissionPlan plan;

    private int rowNumber;
    private String applicationCode;
    private String citizenId;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private String phone;
    private BigDecimal entranceScore;

    @Enumerated(EnumType.STRING)
    private State state = State.VALID;

    private String errorMessage;
    private String warningMessage;
    private boolean reuseAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private AdministrativeClass administrativeClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    public boolean isPending() {
        return state == State.VALID || state == State.FAILED;
    }

    public boolean isIncluded() {
        return isPending() || state == State.ENROLLED;
    }

    public enum State {
        VALID, // đã kiểm tra, đủ điều kiện phân lớp và xác nhận nhập học. Chưa lưu dữ liệu nhập học chính thức.
        INVALID, // Dữ liệu đầu vào không hợp lệ, Admin cần sửa rồi nhập lại.
        CONFLICT, // Hồ sơ có thông tin mâu thuẫn cần Admin kiểm tra, Không tự ghi đè.
        EXISTING, // Hồ sơ trùng đã tồn tại. Bỏ qua, không cấp thêm MSSV hay tạo thêm tài khoản.
        ENROLLED, // Đã ghi nhận nhập học thành công, kể cả nếu gửi mail thất bại
        FAILED // Hồ sơ đủ điều kiện nhưng gặp lỗi trong bước xác nhận/lưu chính thức
    }
}
