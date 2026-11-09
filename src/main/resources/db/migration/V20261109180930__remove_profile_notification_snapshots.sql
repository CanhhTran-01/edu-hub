-- Kết quả gửi đơn lẻ/GV chỉ còn trong response của thao tác, không lưu bản sao trên hồ sơ.
-- Đã chấp nhận bỏ các giá trị tổng hợp cũ; không dựng lịch sử giả hoặc chuyển sang tài khoản User.
-- Hàng đợi admission_notifications giữ nguyên trạng thái/lỗi/bộ đếm để vận hành retry nhập học.
ALTER TABLE students
    DROP CONSTRAINT chk_students_account_notification_status,
    DROP COLUMN account_notification_status,
    DROP COLUMN account_notification_error;

ALTER TABLE lecturers
    DROP CONSTRAINT chk_lecturer_notification_status,
    DROP COLUMN account_notification_status,
    DROP COLUMN account_notification_error;
