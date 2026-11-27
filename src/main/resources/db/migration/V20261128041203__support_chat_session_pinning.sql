-- Hội thoại hiện có mặc định chưa ghim; giữ nguyên nội dung, metadata và trạng thái xóa mềm.
ALTER TABLE chat_sessions
    ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE;

-- Lọc chủ sở hữu và phiên chưa xóa trước khi ưu tiên ghim, thời điểm bắt đầu và ID.
CREATE INDEX idx_chat_sessions_user_pinned_started_at
    ON chat_sessions(user_id, is_pinned DESC, started_at DESC, id DESC)
    WHERE NOT is_deleted;
