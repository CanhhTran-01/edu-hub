-- Danh mục bắt buộc khi chạy ứng dụng (runtime catalog), được áp dụng ở mọi môi trường.
-- Các vai trò (role) này được dùng cho việc tạo tài khoản và cho bước khởi tạo admin ở môi trường dev/local.
-- chúng không phải người dùng mẫu và không được phụ thuộc vào vị trí (thư mục/file) của dữ liệu seed dev.
-- NOTE: SYSTEM_ADMIN do ApplicationInitConfig tạo; INSERT bên dưới chỉ giữ ba role nghiệp vụ.
INSERT INTO roles (role_name, created_at, updated_at)
VALUES
    ('STUDENT', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    ('LECTURER', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    ('ACADEMIC_ADMIN', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC');
