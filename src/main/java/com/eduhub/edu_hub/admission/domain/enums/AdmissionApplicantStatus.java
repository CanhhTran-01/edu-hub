package com.eduhub.edu_hub.admission.domain.enums;

/** Trạng thái hồ sơ nhập học; kết quả gửi mail được lưu riêng tại Notification, không thay đổi trạng thái này. */
public enum AdmissionApplicantStatus {
    // ===== Nhóm 1: Chưa vào được phương án (không tính sĩ số) =====
    INVALID, // Dữ liệu đầu vào không hợp lệ, Admin sửa đúng dòng bằng applicantId.
    CONFLICT, // Thông tin mâu thuẫn, Admin kiểm tra, không tự ghi đè.
    EXISTING, // Hồ sơ đã tồn tại, bỏ qua, không cấp MSSV/tài khoản mới.

    // NOTE: File bổ sung chỉ thêm mới; INVALID/CONFLICT được sửa qua API riêng và đối chiếu lại nhóm liên quan.
    // NOTE: Có thể bỏ INVALID/CONFLICT bằng xóa mềm; dòng đã bỏ không tham gia xử lý và không cần trạng thái mới.

    // ===== Nhóm 2: Đủ điều kiện, còn chờ xác nhận nhập học (tính sĩ số) =====
    VALID, // Đã kiểm tra, đủ điều kiện phân lớp. Chưa lưu dữ liệu nhập học chính thức.
    FAILED, // Đủ điều kiện nhưng lỗi ở bước xác nhận/lưu chính thức, có thể thử lại.

    // ===== Nhóm 3: Hoàn tất (tính sĩ số) =====
    ENROLLED // Đã ghi nhận nhập học, kể cả khi gửi mail thất bại.
}
