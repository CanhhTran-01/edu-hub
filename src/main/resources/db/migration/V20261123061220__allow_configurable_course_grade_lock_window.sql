-- Hạn khóa được chốt từ cấu hình khi công bố; giữ nguyên mọi hạn và kết quả đã lưu.
-- Cho phép hạn bằng mốc công bố để cấu hình 0 khóa ngay, không cố định số ngày trong schema.
ALTER TABLE enrollments DROP CONSTRAINT chk_enrollments_grade_publication;

ALTER TABLE enrollments ADD CONSTRAINT chk_enrollments_grade_publication CHECK (
    (grade_published_at IS NULL AND grade_lock_at IS NULL)
    OR (grade_published_at IS NOT NULL AND grade_lock_at IS NOT NULL
        AND grade_lock_at >= grade_published_at
        AND course_score_grade_10 IS NOT NULL)
);
