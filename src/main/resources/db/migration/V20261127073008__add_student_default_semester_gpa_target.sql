-- Mục tiêu kỳ mặc định thuộc hồ sơ sinh viên, không lưu riêng theo từng kỳ hoặc sao chép từ mục tiêu tốt nghiệp.
ALTER TABLE students
    ADD COLUMN semester_target_gpa NUMERIC(19, 2),
    ADD CONSTRAINT ck_students_semester_target_gpa
        CHECK (semester_target_gpa IS NULL OR semester_target_gpa BETWEEN 0 AND 4);
