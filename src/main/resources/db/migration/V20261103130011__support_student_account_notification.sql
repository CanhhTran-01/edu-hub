ALTER TABLE students
    ADD COLUMN account_notification_status VARCHAR(16),
    ADD COLUMN account_notification_error VARCHAR(255);

ALTER TABLE students
    ADD CONSTRAINT chk_students_account_notification_status
        CHECK (account_notification_status IS NULL OR account_notification_status IN ('PENDING', 'SENT', 'FAILED'));

UPDATE students SET email = LOWER(BTRIM(email)) WHERE email IS NOT NULL;

UPDATE students SET citizen_id = REGEXP_REPLACE(citizen_id, '\s+', '', 'g') WHERE citizen_id IS NOT NULL;

CREATE UNIQUE INDEX uk_students_active_email_normalized ON students (LOWER(BTRIM(email))) WHERE is_deleted = FALSE AND email IS NOT NULL;
CREATE UNIQUE INDEX uk_students_active_citizen_id ON students (citizen_id) WHERE is_deleted = FALSE AND citizen_id IS NOT NULL;