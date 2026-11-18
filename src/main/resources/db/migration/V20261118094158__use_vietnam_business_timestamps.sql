-- Chuyển các mốc nghiệp vụ đã lưu theo UTC sang giờ Việt Nam, giữ nguyên thời điểm thực tế.
-- Chỉ chạy một lần qua Flyway khi ứng dụng cũ đã dừng; created_at/updated_at vẫn giữ UTC.
-- Giữ cả dữ liệu đã xóa mềm và các mốc null để không làm sai lịch sử hoặc kéo dài thời hạn.

UPDATE users
SET locked_until = (locked_until AT TIME ZONE 'UTC') AT TIME ZONE 'Asia/Ho_Chi_Minh',
    password_changed_at = (password_changed_at AT TIME ZONE 'UTC') AT TIME ZONE 'Asia/Ho_Chi_Minh'
WHERE locked_until IS NOT NULL OR password_changed_at IS NOT NULL;

UPDATE refresh_tokens
SET expires_at = (expires_at AT TIME ZONE 'UTC') AT TIME ZONE 'Asia/Ho_Chi_Minh';

UPDATE admission_plans
SET processing_until = (processing_until AT TIME ZONE 'UTC') AT TIME ZONE 'Asia/Ho_Chi_Minh'
WHERE processing_until IS NOT NULL;

UPDATE admission_notifications
SET next_attempt_at = (next_attempt_at AT TIME ZONE 'UTC') AT TIME ZONE 'Asia/Ho_Chi_Minh';

UPDATE timetable_plans
SET expires_at = (expires_at AT TIME ZONE 'UTC') AT TIME ZONE 'Asia/Ho_Chi_Minh',
    applied_at = (applied_at AT TIME ZONE 'UTC') AT TIME ZONE 'Asia/Ho_Chi_Minh';

UPDATE enrollments
SET registered_at = (registered_at AT TIME ZONE 'UTC') AT TIME ZONE 'Asia/Ho_Chi_Minh',
    dropped_at = (dropped_at AT TIME ZONE 'UTC') AT TIME ZONE 'Asia/Ho_Chi_Minh';
