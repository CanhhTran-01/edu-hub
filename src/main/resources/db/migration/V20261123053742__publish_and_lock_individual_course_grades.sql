-- Khóa điểm theo từng lượt học; không suy mốc công bố lịch sử từ created_at/updated_at.
-- Kết quả cũ thiếu mốc công bố giữ chỉ đọc trong ứng dụng, không tự mở thêm 14 ngày.
ALTER TABLE enrollments
    ADD COLUMN grade_published_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN grade_lock_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE enrollments ADD CONSTRAINT chk_enrollments_grade_publication CHECK (
    (grade_published_at IS NULL AND grade_lock_at IS NULL)
    OR (grade_published_at IS NOT NULL AND grade_lock_at IS NOT NULL
        AND grade_lock_at = grade_published_at + INTERVAL '14 days'
        AND course_score_grade_10 IS NOT NULL)
);

-- Cờ khóa toàn kỳ chưa được ứng dụng thực thi; is_locked cho nghiệp vụ kỳ học vẫn được giữ.
ALTER TABLE semesters DROP COLUMN locked_grade;
