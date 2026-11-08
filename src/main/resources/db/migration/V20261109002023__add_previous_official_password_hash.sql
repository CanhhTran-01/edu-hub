-- Chỉ lưu một mật khẩu chính thức trước đó; không lưu mật khẩu tạm hoặc lịch sử nhiều đời.
ALTER TABLE users ADD COLUMN previous_password_hash VARCHAR(255);

-- Dữ liệu cũ không có lịch sử để khôi phục; để NULL và bắt đầu ghi từ lần đổi/reset tiếp theo.
COMMENT ON COLUMN users.previous_password_hash IS 'BCrypt của một mật khẩu chính thức ngay trước mật khẩu hiện tại';
