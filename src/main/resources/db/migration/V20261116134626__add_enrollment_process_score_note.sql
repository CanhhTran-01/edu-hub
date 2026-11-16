-- Ghi chú thuộc từng lượt học; dữ liệu cũ chưa có ghi chú giữ chuỗi rỗng.
ALTER TABLE enrollments ADD COLUMN note VARCHAR(500) NOT NULL DEFAULT '';
