-- Mỗi token hiện có là một phiên độc lập; không đổi hash, hạn, chủ sở hữu hoặc trạng thái đã lưu.
ALTER TABLE refresh_tokens
    ADD COLUMN token_family_id UUID NOT NULL DEFAULT gen_random_uuid();

-- Giữ quan hệ các lần xoay để phát hiện dùng lại token đã thu hồi mà không gộp các phiên khác nhau.
CREATE INDEX idx_refresh_tokens_active_family
    ON refresh_tokens (token_family_id) WHERE revoked = FALSE;

-- Job dọn theo hạn tuyệt đối và ID, áp dụng cả token đã revoke và chưa revoke.
CREATE INDEX idx_refresh_tokens_expiration ON refresh_tokens (expires_at, id);
