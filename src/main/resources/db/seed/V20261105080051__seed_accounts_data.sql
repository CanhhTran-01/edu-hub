-- Tài khoản test chỉ dùng cho môi trường phát triển
-- Mật khẩu chung: Postman@123 (lưu dạng BCrypt), tạm thời không bắt buộc đổi mật khẩu lần đầu.
-- Không ghi đè tài khoản trùng username, chỉ tạo tài khoản đăng nhập, chưa tạo hồ sơ People.
INSERT INTO users (
    username, email, password_hash, role_id, status, is_first_login,
    created_at, updated_at, created_by, updated_by
)
SELECT seed.username, seed.email,
       '$2a$10$3xy5eW4YCYkY5E1w47sFuu67Llw.MGGDxMDFyhdt3y0i6uWAxKCH.',
       role.id, 'ACTIVE', FALSE,
       CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
       'postman-seed', 'postman-seed'
FROM (VALUES
    ('postman_academic_admin', 'postman-academic-admin@example.test', 'ACADEMIC_ADMIN'),
    ('postman_lecturer', 'postman-lecturer@example.test', 'LECTURER'),
    ('postman_student', 'postman-student@example.test', 'STUDENT')
) AS seed(username, email, role_name)
JOIN roles role ON role.role_name = seed.role_name AND role.is_deleted = FALSE
ON CONFLICT (username) DO NOTHING;
