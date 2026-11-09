-- User là nguồn duy nhất của email hiện tại; không sửa email lịch sử nhập học hoặc người nhận thông báo.
-- Đã chấp nhận bỏ email cũ trên Student/Lecturer, kể cả hồ sơ chưa gắn User hoặc khác email tài khoản.
-- Giữ nguyên users.email, kể cả NULL; không khôi phục email từ hồ sơ hoặc tạo tài khoản để giữ email cũ.
LOCK TABLE users, students, lecturers IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    duplicate_lecturer_email_count BIGINT;
BEGIN
    -- Phạm vi duy nhất của giảng viên theo hồ sơ chưa xóa, không theo role hoặc trạng thái ACTIVE/INACTIVE.
    SELECT count(*) INTO duplicate_lecturer_email_count
    FROM (
        SELECT lower(btrim(account.email))
        FROM lecturers lecturer JOIN users account ON account.id = lecturer.user_id
        WHERE lecturer.is_deleted = FALSE AND account.email IS NOT NULL
        GROUP BY lower(btrim(account.email)) HAVING count(*) > 1
    ) duplicate_emails;
    IF duplicate_lecturer_email_count > 0 THEN
        RAISE EXCEPTION 'Có % nhóm email User trùng trong hồ sơ giảng viên chưa xóa; cần đối soát trước khi chuyển đổi',
            duplicate_lecturer_email_count;
    END IF;
END $$;

-- Cờ kỹ thuật chỉ đánh dấu phạm vi unique index, không chứa email thứ hai và không dùng cho phân quyền.
-- Không map cờ vào entity User để ứng dụng không ghi đè trạng thái do trigger quản lý.
ALTER TABLE users ADD COLUMN has_undeleted_lecturer_profile BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users account
SET has_undeleted_lecturer_profile = EXISTS (
    SELECT 1 FROM lecturers lecturer
    WHERE lecturer.user_id = account.id AND lecturer.is_deleted = FALSE
);

DROP INDEX IF EXISTS uk_lecturers_email_active;
-- Giữ tên ràng buộc để handler tạo giảng viên tiếp tục trả 409/4117; sinh viên vẫn được dùng chung email.
CREATE UNIQUE INDEX uk_lecturers_email_active ON users (lower(btrim(email)))
WHERE has_undeleted_lecturer_profile = TRUE AND email IS NOT NULL;

CREATE FUNCTION synchronize_lecturer_email_unique_scope() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    affected_user_ids BIGINT[];
BEGIN
    IF TG_OP = 'INSERT' THEN
        affected_user_ids := ARRAY[NEW.user_id];
    ELSIF TG_OP = 'DELETE' THEN
        affected_user_ids := ARRAY[OLD.user_id];
    ELSE
        affected_user_ids := ARRAY[OLD.user_id, NEW.user_id];
    END IF;

    -- Khóa User theo ID tăng dần khi đổi liên kết; unique index bảo vệ cả ghi email và gắn/khôi phục hồ sơ.
    PERFORM account.id FROM users account
    WHERE account.id = ANY(affected_user_ids)
    ORDER BY account.id FOR UPDATE;

    -- Giải phóng phạm vi của User cũ trước khi thêm User mới, kể cả User mới có ID nhỏ hơn.
    -- Nếu hai User cùng email, đổi liên kết của một hồ sơ không được gây lỗi trùng tạm thời ở giữa trigger.
    UPDATE users account SET has_undeleted_lecturer_profile = FALSE
    WHERE account.id = ANY(affected_user_ids)
      AND account.has_undeleted_lecturer_profile = TRUE
      AND NOT EXISTS (
          SELECT 1 FROM lecturers lecturer
          WHERE lecturer.user_id = account.id AND lecturer.is_deleted = FALSE
      );
    UPDATE users account SET has_undeleted_lecturer_profile = TRUE
    WHERE account.id = ANY(affected_user_ids)
      AND account.has_undeleted_lecturer_profile = FALSE
      AND EXISTS (
          SELECT 1 FROM lecturers lecturer
          WHERE lecturer.user_id = account.id AND lecturer.is_deleted = FALSE
      );
    RETURN NULL;
END $$;

CREATE TRIGGER trg_lecturers_email_unique_scope
    AFTER INSERT OR DELETE OR UPDATE OF user_id, is_deleted ON lecturers
    FOR EACH ROW EXECUTE FUNCTION synchronize_lecturer_email_unique_scope();

ALTER TABLE students DROP COLUMN email;
ALTER TABLE lecturers DROP COLUMN email;
