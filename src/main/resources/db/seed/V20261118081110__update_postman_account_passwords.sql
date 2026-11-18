-- Đổi mật khẩu chung của ba tài khoản Postman mẫu; chỉ chạy khi bật location db/seed.
-- Chỉ cập nhật tài khoản do seed gốc tạo, không ghi đè User trùng username được tạo từ nguồn khác.
-- Giữ nguyên role, trạng thái, cờ đổi mật khẩu lần đầu và liên kết hồ sơ; thu hồi các phiên cũ.
WITH updated_seed_accounts AS (
    UPDATE users AS seeded_account
    SET password_hash = '$2a$10$5biN5Ln1cTU4iw5qY3QqY.DUcl.sZkQDdEas4fWGCaHU/z6U.tVGi',
        previous_password_hash = CASE
            WHEN seeded_account.is_first_login THEN seeded_account.previous_password_hash
            ELSE seeded_account.password_hash
        END,
        password_changed_at = CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
        updated_at = CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
        updated_by = 'postman-seed'
    WHERE seeded_account.username IN ('academic_admin', 'lecturer', 'student')
      AND seeded_account.created_by = 'postman-seed'
      AND seeded_account.is_deleted = FALSE
    RETURNING seeded_account.id
)
UPDATE refresh_tokens AS account_session
SET revoked = TRUE,
    updated_at = CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
    updated_by = 'postman-seed'
WHERE account_session.user_id IN (SELECT id FROM updated_seed_accounts)
  AND account_session.revoked = FALSE;
