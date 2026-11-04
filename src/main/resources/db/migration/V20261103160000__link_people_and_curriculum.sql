-- Giữ trống các thuộc tính chưa được cấu hình trong dữ liệu cũ, không suy năm học
-- từ mã khóa hoặc tự gán khoa/khóa tuyển sinh cho một chương trình đào tạo.
ALTER TABLE cohorts
    ADD COLUMN start_year INTEGER,
    ADD COLUMN end_year INTEGER,
    ADD CONSTRAINT chk_cohorts_years CHECK (
        (start_year IS NULL AND end_year IS NULL)
        OR (start_year IS NOT NULL AND end_year IS NOT NULL
            AND start_year BETWEEN 1 AND 9998 AND end_year BETWEEN 2 AND 9999 AND end_year > start_year)
    );

ALTER TABLE majors
    ADD COLUMN faculty_id BIGINT,
    ADD CONSTRAINT fk_majors_faculty FOREIGN KEY (faculty_id) REFERENCES faculties(id);
CREATE INDEX idx_majors_faculty ON majors(faculty_id);

-- Đối soát tham chiếu cũ trước khi bỏ tên ngành sao chép trong lớp hành chính.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM administrative_classes c
        LEFT JOIN majors m ON m.id = c.major_id
        WHERE m.id IS NULL OR m.major_name <> c.major_name
    ) THEN
        RAISE EXCEPTION 'Administrative class major IDs/names must be reconciled before linking to curriculum';
    END IF;
END $$;

ALTER TABLE administrative_classes
    ADD CONSTRAINT fk_administrative_classes_major FOREIGN KEY (major_id) REFERENCES majors(id),
    DROP COLUMN major_name;

ALTER TABLE curriculums
    ADD COLUMN cohort_id BIGINT,
    ADD COLUMN required_graduation_credits INTEGER,
    ADD CONSTRAINT fk_curriculums_cohort FOREIGN KEY (cohort_id) REFERENCES cohorts(id),
    ADD CONSTRAINT uk_curriculums_major_cohort_training UNIQUE (major_id, cohort_id, training_type);

-- CTĐT hiện tại chỉ có môn REQUIRED. Khởi tạo một lần từ các yêu cầu hiện có;
-- những thay đổi danh mục sau này không tự sửa yêu cầu tín chỉ tốt nghiệp.
UPDATE curriculums c
SET required_graduation_credits = (
    SELECT SUM(s.credits)
    FROM curriculum_subjects cs
    JOIN subjects s ON s.id = cs.subject_id
    WHERE cs.curriculum_id = c.id AND cs.subject_type = 'REQUIRED'
        AND cs.is_deleted = FALSE AND s.is_deleted = FALSE
);

ALTER TABLE curriculums
    ADD CONSTRAINT chk_curriculums_graduation_credits CHECK (required_graduation_credits > 0);

-- CTĐT cũ không có môn bắt buộc cần được cấu hình riêng, tránh tự chọn một mức tín chỉ.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM curriculums WHERE required_graduation_credits IS NULL) THEN
        RAISE EXCEPTION 'Curriculums without required subjects need graduation credits configured before migration';
    END IF;
END $$;
ALTER TABLE curriculums ALTER COLUMN required_graduation_credits SET NOT NULL;

ALTER TABLE students ADD COLUMN academic_warning_reason TEXT;

-- FK mới vẫn kiểm tra mọi liên kết mới. ID giảng viên fixture còn sót phải được
-- đối soát riêng; không đổi hoặc xóa giảng viên của lớp cũ một cách tự động.
ALTER TABLE module_classes ADD CONSTRAINT fk_module_classes_lecturer
    FOREIGN KEY (lecturer_id) REFERENCES lecturers(id) NOT VALID;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM module_classes m
        LEFT JOIN lecturers l ON l.id = m.lecturer_id
        WHERE m.lecturer_id IS NOT NULL AND l.id IS NULL
    ) THEN
        ALTER TABLE module_classes VALIDATE CONSTRAINT fk_module_classes_lecturer;
    END IF;
END $$;
