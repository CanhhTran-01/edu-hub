ALTER TABLE students
    ADD CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users(id),
    ADD CONSTRAINT uk_students_user UNIQUE (user_id);
ALTER TABLE lecturers
    ADD CONSTRAINT fk_lecturers_user FOREIGN KEY (user_id) REFERENCES users(id),
    ADD CONSTRAINT uk_lecturers_user UNIQUE (user_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM students s JOIN lecturers l ON l.user_id = s.user_id) THEN
        RAISE EXCEPTION 'A user is already linked to both a student and a lecturer; reconcile those links first';
    END IF;
END $$;

-- Một khóa duy nhất (unique key) dùng chung đảm bảo tính loại trừ lẫn nhau giữa cả hai bảng,
-- kể cả với các giao dịch chạy đồng thời và các giao dịch dùng mức cô lập REPEATABLE READ.
-- Đây là bảng nội bộ phục vụ toàn vẹn dữ liệu, không phải một hồ sơ (profile) khác hay nhật ký kiểm toán (audit trail).
CREATE TABLE people_user_links (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    student_id BIGINT UNIQUE REFERENCES students(id) ON DELETE CASCADE,
    lecturer_id BIGINT UNIQUE REFERENCES lecturers(id) ON DELETE CASCADE,
    CONSTRAINT chk_people_user_link_profile CHECK (
        (student_id IS NOT NULL AND lecturer_id IS NULL)
        OR (student_id IS NULL AND lecturer_id IS NOT NULL)
    )
);
INSERT INTO people_user_links (user_id, student_id)
SELECT user_id, id FROM students WHERE user_id IS NOT NULL;
INSERT INTO people_user_links (user_id, lecturer_id)
SELECT user_id, id FROM lecturers WHERE user_id IS NOT NULL;

-- Đồng bộ các bản ghi gán (assignments) trong cùng một giao dịch với thao tác ghi hồ sơ.
-- Việc xóa mềm một hồ sơ không giải phóng tài khoản của nó để gán cho hồ sơ khác.
CREATE FUNCTION enforce_exclusive_people_user_link() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.user_id IS NOT DISTINCT FROM NEW.user_id THEN
        RETURN NEW;
    END IF;
    IF TG_TABLE_NAME = 'students' THEN
        DELETE FROM people_user_links WHERE student_id = NEW.id;
        IF NEW.user_id IS NOT NULL THEN
            INSERT INTO people_user_links (user_id, student_id) VALUES (NEW.user_id, NEW.id);
        END IF;
    ELSE
        DELETE FROM people_user_links WHERE lecturer_id = NEW.id;
        IF NEW.user_id IS NOT NULL THEN
            INSERT INTO people_user_links (user_id, lecturer_id) VALUES (NEW.user_id, NEW.id);
        END IF;
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_students_exclusive_user
    AFTER INSERT OR UPDATE OF user_id ON students
    FOR EACH ROW EXECUTE FUNCTION enforce_exclusive_people_user_link();
CREATE TRIGGER trg_lecturers_exclusive_user
    AFTER INSERT OR UPDATE OF user_id ON lecturers
    FOR EACH ROW EXECUTE FUNCTION enforce_exclusive_people_user_link();
