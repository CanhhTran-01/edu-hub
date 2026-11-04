-- Các quan hệ entity tiếp tục dùng major_id, subject_id và lecturer_id cùng các FK hiện có.
-- Mã ngành chuẩn thuộc majors; tiền tố mã sinh viên vẫn do OfficialStudentCodeGenerator ánh xạ riêng.
DROP INDEX idx_administrative_classes_admission_scope;

CREATE INDEX idx_administrative_classes_admission_scope
    ON administrative_classes (major_id, cohort_id, training_type)
    WHERE is_deleted = FALSE;

ALTER TABLE administrative_classes DROP COLUMN major_code;
