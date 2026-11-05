-- Chỉnh sửa: Mở lớp học phần và phân công giảng viên sẽ tách thành 2 use-case riêng.
-- Giữ nguyên giảng viên đã gán cho các lớp cũ
-- Lớp học phần mới được tạo có thể chưa phân công giảng viên.
ALTER TABLE module_classes ALTER COLUMN lecturer_id DROP NOT NULL;
