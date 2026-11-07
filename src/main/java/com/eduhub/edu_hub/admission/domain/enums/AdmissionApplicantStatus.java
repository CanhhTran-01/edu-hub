package com.eduhub.edu_hub.admission.domain.enums;

public enum AdmissionApplicantStatus {
    VALID, // đã kiểm tra, đủ điều kiện phân lớp và xác nhận nhập học. Chưa lưu dữ liệu nhập học chính thức.
    INVALID, // Dữ liệu đầu vào không hợp lệ, Admin cần sửa rồi nhập lại.
    CONFLICT, // Hồ sơ có thông tin mâu thuẫn cần Admin kiểm tra, Không tự ghi đè.
    EXISTING, // Hồ sơ trùng đã tồn tại. Bỏ qua, không cấp thêm MSSV hay tạo thêm tài khoản.
    ENROLLED, // Đã ghi nhận nhập học thành công, kể cả nếu gửi mail thất bại
    FAILED // Hồ sơ đủ điều kiện nhưng gặp lỗi trong bước xác nhận/lưu chính thức
}
