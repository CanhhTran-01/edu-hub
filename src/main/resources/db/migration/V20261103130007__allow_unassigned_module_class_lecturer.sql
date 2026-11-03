-- Mở lớp và phân công giảng viên là hai use-case riêng.
-- Giữ nguyên giảng viên đã gán cho các lớp cũ; lớp mới có thể chưa phân công.
ALTER TABLE module_classes ALTER COLUMN lecturer_id DROP NOT NULL;
