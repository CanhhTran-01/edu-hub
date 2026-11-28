-- Ba tài khoản student/lecturer/academic_admin dùng mật khẩu mẫu trong samples/init-account-for-local-testing.md.
-- Các tài khoản MSSV/MSGV còn lại dùng mật khẩu khởi tạo 12345; mọi mật khẩu đều lưu BCrypt.
-- SYSTEM_ADMIN và system_admin vẫn do ApplicationInitConfig khởi tạo, không tạo trong seed.
-- Hai username student/lecturer là bí danh local; các tài khoản còn lại dùng đúng MSSV/MSGV.
CREATE TEMP TABLE demo_student_profiles ON COMMIT DROP AS
WITH numbered_classes AS (
    SELECT administrative_class.*, cohort.start_year, cohort.code_sequence, major.major_code,
           row_number() OVER (PARTITION BY major.id, cohort.id ORDER BY administrative_class.class_name)::INTEGER AS class_position
    FROM administrative_classes administrative_class JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id
    JOIN majors major ON major.id = administrative_class.major_id
), numbered_students AS (
    SELECT numbered_classes.*, student_sequence,
           row_number() OVER (ORDER BY start_year, major_code, class_position, student_sequence)::INTEGER AS student_ordinal,
           CASE major_code WHEN 'CNTT' THEN 'CT' WHEN 'ATTT' THEN 'AT' WHEN 'DTVT' THEN 'DT' ELSE 'AI' END
               || lpad(code_sequence::TEXT, 2, '0') || lpad(class_position::TEXT, 2, '0')
               || lpad(student_sequence::TEXT, 2, '0') AS student_code,
           start_year = 2023 AND major_code = 'CNTT' AND class_position = 1 AND student_sequence = 1 AS is_demo_student
    FROM numbered_classes CROSS JOIN LATERAL generate_series(1, last_student_sequence) AS student_sequence
)
SELECT id AS class_id, start_year, major_code, class_position, student_sequence, student_ordinal, student_code,
       CASE WHEN is_demo_student THEN 'student' ELSE student_code END AS username,
       (ARRAY['Nguyễn','Trần','Lê','Phạm','Hoàng','Huỳnh','Phan','Vũ','Võ','Đặng','Bùi','Đỗ','Hồ','Ngô','Dương','Lý','Đinh','Trịnh','Đoàn','Mai'])
           [1 + (student_ordinal % 20)] || ' ' ||
       CASE WHEN student_ordinal % 2 = 0 THEN
           (ARRAY['Văn','Minh','Đức','Quang','Hữu','Thanh','Trọng','Gia'])[1 + ((student_ordinal / 20) % 8)] || ' ' ||
           (ARRAY['An','Bảo','Cường','Dũng','Đạt','Duy','Hải','Hiếu','Hoàng','Huy','Khang','Khánh','Kiên','Long','Nam','Nguyên','Phong','Phúc','Quân','Sơn','Thành','Thiện','Trí','Trung','Tuấn','Việt','Vinh','Bình','Lâm','Tùng'])[1 + ((student_ordinal / 7) % 30)]
       ELSE
           (ARRAY['Thị','Ngọc','Thu','Thanh','Khánh','Bảo','Phương','Diệu'])[1 + ((student_ordinal / 20) % 8)] || ' ' ||
           (ARRAY['Anh','Ánh','Chi','Châu','Diệp','Dung','Duyên','Giang','Hà','Hân','Hạnh','Hoa','Hương','Huyền','Khánh','Lan','Linh','Ly','Mai','My','Nga','Ngân','Nhung','Oanh','Phương','Quỳnh','Thảo','Trang','Trâm','Yến'])[1 + ((student_ordinal / 7) % 30)] END AS full_name,
       make_date(start_year - 18, 1, 1) + ((student_ordinal * 7) % 365) AS date_of_birth,
       CASE WHEN student_ordinal % 2 = 0 THEN 'NAM' ELSE 'NU' END AS gender,
       '999' || CASE WHEN student_ordinal % 2 = 0 THEN '2' ELSE '3' END
           || right((start_year - 18)::TEXT, 2) || lpad(student_ordinal::TEXT, 6, '0') AS citizen_id,
       '09' || lpad((10000000 + student_ordinal)::TEXT, 8, '0') AS phone,
       CASE WHEN is_demo_student THEN 'postman-student@example.test'
            WHEN student_ordinal % 41 = 0 THEN NULL ELSE lower(student_code) || '@students.example.test' END AS email,
       round(21 + (student_ordinal % 90)::NUMERIC / 10, 2) AS entrance_score,
       CASE WHEN student_ordinal % 7 = 0 THEN NULL ELSE (2.5 + (student_ordinal % 15)::NUMERIC / 10) END AS target_gpa,
       CASE WHEN start_year <= 2022 OR (student_ordinal % 137 = 0 AND NOT is_demo_student)
            THEN 'DEACTIVATED' ELSE 'STUDYING' END AS activity_status,
       NOT is_demo_student AND student_ordinal % 211 = 0 AS is_deleted,
       NOT is_demo_student AND start_year = 2025 AND student_ordinal % 19 = 0 AS is_first_login,
       CASE WHEN is_demo_student THEN 'ACTIVE' WHEN student_ordinal % 173 = 0 THEN 'DISABLED'
            WHEN student_ordinal % 101 = 0 THEN 'LOCKED' ELSE 'ACTIVE' END AS account_status,
       'HS-' || start_year || '-' || major_code || '-' || lpad((class_position * 100 + student_sequence)::TEXT, 4, '0') AS application_code
FROM numbered_students;

INSERT INTO users (username, email, password_hash, role_id, status, is_first_login, is_deleted,
                   password_changed_at, created_at, updated_at, created_by, updated_by)
SELECT profile.username, profile.email,
       CASE WHEN profile.username = 'student' THEN '$2a$10$5biN5Ln1cTU4iw5qY3QqY.DUcl.sZkQDdEas4fWGCaHU/z6U.tVGi'
            ELSE '$2a$10$3hAK6XRYAHeK69z274TVF.Y71A.1BH94xWtuxy1/KFRwRGjKH3vK.' END, role.id, profile.account_status,
       profile.is_first_login, profile.is_deleted,
       CASE WHEN profile.is_first_login THEN NULL ELSE make_date(profile.start_year, 7, 17) + TIME '09:00' END,
       make_date(profile.start_year, 7, 16) + TIME '01:00', make_date(profile.start_year, 7, 17) + TIME '02:00',
       'demo-seed', 'demo-seed'
FROM demo_student_profiles profile CROSS JOIN roles role WHERE role.role_name = 'STUDENT'
ORDER BY profile.student_ordinal;

INSERT INTO students (user_id, class_id, student_code, full_name, citizen_id, date_of_birth, phone, gender,
                      entrance_score, target_gpa, activity_status, admission_application_code, is_deleted,
                      created_at, updated_at, created_by, updated_by)
SELECT account.id, profile.class_id, profile.student_code, profile.full_name, profile.citizen_id,
       profile.date_of_birth, profile.phone, profile.gender, profile.entrance_score, profile.target_gpa,
       profile.activity_status, profile.application_code, profile.is_deleted,
       make_date(profile.start_year, 7, 16) + TIME '01:00', TIMESTAMP '2026-11-23 02:00:00', 'demo-seed', 'demo-seed'
FROM demo_student_profiles profile JOIN users account ON account.username = profile.username
ORDER BY profile.student_ordinal;

CREATE TEMP TABLE demo_lecturer_profiles ON COMMIT DROP AS
WITH lecturer_sequences AS (
    SELECT faculty.id AS faculty_id, faculty.faculty_code, lecturer_sequence,
           CASE faculty.faculty_code WHEN 'TTNT' THEN 2021 ELSE 2016 END AS recruitment_year,
           CASE faculty.faculty_code WHEN 'CNTT' THEN 'CT' WHEN 'ATTT' THEN 'AT' WHEN 'DTVT' THEN 'DT'
                WHEN 'TTNT' THEN 'AI' ELSE 'CB' END AS code_prefix,
           row_number() OVER (ORDER BY faculty.faculty_code, lecturer_sequence)::INTEGER AS lecturer_ordinal
    FROM faculties faculty CROSS JOIN generate_series(1,24) AS lecturer_sequence
)
SELECT faculty_id, faculty_code, lecturer_sequence, lecturer_ordinal, recruitment_year,
       code_prefix || right(recruitment_year::TEXT, 2) || lpad(lecturer_sequence::TEXT, 3, '0') AS lecturer_code,
       CASE WHEN faculty_code = 'CNTT' AND lecturer_sequence = 1 THEN 'lecturer'
            ELSE code_prefix || right(recruitment_year::TEXT, 2) || lpad(lecturer_sequence::TEXT, 3, '0') END AS username,
       (ARRAY['Nguyễn','Trần','Lê','Phạm','Đặng','Vũ','Hoàng','Đỗ'])[1 + (lecturer_ordinal % 8)] || ' ' ||
       CASE WHEN lecturer_ordinal % 2 = 0 THEN 'Minh ' ELSE 'Thu ' END ||
       (ARRAY['Anh','Bình','Châu','Dũng','Giang','Hà','Hải','Hương','Khánh','Lan','Long','Mai','Nam','Ngân','Phương','Quân','Sơn','Thành','Thảo','Trang','Trí','Trung','Tuấn','Vân'])[1 + ((lecturer_ordinal / 3) % 24)] AS full_name,
       make_date(1970 + (lecturer_ordinal % 20), 1, 1) + ((lecturer_ordinal * 13) % 365) AS date_of_birth,
       '998' || lpad(lecturer_ordinal::TEXT, 9, '0') AS citizen_id,
       '098' || lpad(lecturer_ordinal::TEXT, 7, '0') AS phone,
       lower(code_prefix) || '.lecturer.' || lpad(lecturer_sequence::TEXT, 3, '0') || '@staff.example.test' AS email,
       CASE WHEN lecturer_sequence >= 23
            THEN 'INACTIVE' ELSE 'ACTIVE' END AS status,
       lecturer_sequence = 24 AS is_deleted
FROM lecturer_sequences;

INSERT INTO users (username, email, password_hash, role_id, status, is_first_login, is_deleted,
                   password_changed_at, created_at, updated_at, created_by, updated_by)
SELECT profile.username, profile.email,
       CASE WHEN profile.username = 'lecturer' THEN '$2a$10$5biN5Ln1cTU4iw5qY3QqY.DUcl.sZkQDdEas4fWGCaHU/z6U.tVGi'
            ELSE '$2a$10$3hAK6XRYAHeK69z274TVF.Y71A.1BH94xWtuxy1/KFRwRGjKH3vK.' END, role.id, 'ACTIVE', FALSE, profile.is_deleted,
       make_date(profile.recruitment_year, 6, 2) + TIME '09:00',
       make_date(profile.recruitment_year, 6, 1) + TIME '01:00', make_date(profile.recruitment_year, 6, 2) + TIME '02:00',
       'demo-seed', 'demo-seed'
FROM demo_lecturer_profiles profile CROSS JOIN roles role WHERE role.role_name = 'LECTURER';

INSERT INTO lecturers (user_id, faculty_id, lecturer_code, full_name, date_of_birth, citizen_id, phone,
                       recruitment_year, status, is_deleted, created_at, updated_at, created_by, updated_by)
SELECT account.id, profile.faculty_id, profile.lecturer_code, profile.full_name, profile.date_of_birth,
       profile.citizen_id, profile.phone, profile.recruitment_year, profile.status, profile.is_deleted,
       make_date(profile.recruitment_year, 6, 1) + TIME '01:00', make_date(profile.recruitment_year, 6, 1) + TIME '01:00',
       'demo-seed', 'demo-seed'
FROM demo_lecturer_profiles profile JOIN users account ON account.username = profile.username;

-- Bộ đếm bao gồm hồ sơ đã xóa, tránh cấp lại mã khi thêm giảng viên qua API.
INSERT INTO lecturer_code_counters (faculty_id, recruitment_year, last_lecturer_sequence)
SELECT faculty_id, recruitment_year, max(lecturer_sequence)
FROM demo_lecturer_profiles GROUP BY faculty_id, recruitment_year;

INSERT INTO lecturer_teaching_subjects (lecturer_id, subject_id, created_at, updated_at, created_by, updated_by)
SELECT lecturer.id, subject.id, lecturer.created_at, lecturer.updated_at, 'demo-seed', 'demo-seed'
FROM lecturers lecturer JOIN faculties faculty ON faculty.id = lecturer.faculty_id CROSS JOIN subjects subject
WHERE subject.status = 'ACTIVE' AND NOT subject.is_deleted
  AND faculty.faculty_code = CASE
      WHEN subject.subject_code ~ '^(GEN|MATH|SEM)' THEN 'KHCB'
      WHEN subject.subject_code ~ '^SEC' THEN 'ATTT'
      WHEN subject.subject_code ~ '^ET' THEN 'DTVT'
      WHEN subject.subject_code ~ '^AI' OR subject.subject_code = 'CS110' THEN 'TTNT'
      ELSE 'CNTT' END;

INSERT INTO users (username, email, password_hash, role_id, status, is_first_login,
                   password_changed_at, created_at, updated_at, created_by, updated_by)
SELECT 'academic_admin', 'postman-academic-admin@example.test', '$2a$10$5biN5Ln1cTU4iw5qY3QqY.DUcl.sZkQDdEas4fWGCaHU/z6U.tVGi', role.id, 'ACTIVE', FALSE,
       TIMESTAMP '2016-07-01 09:00:00', TIMESTAMP '2016-07-01 02:00:00', TIMESTAMP '2016-07-01 02:00:00',
       'demo-seed', 'demo-seed'
FROM roles role WHERE role.role_name = 'ACADEMIC_ADMIN';

-- Phiên giả lập đều đã hết hạn; không tạo bearer hoặc refresh token có thể dùng đăng nhập.
INSERT INTO refresh_tokens (user_id, token_hash, expires_at, revoked, created_at, updated_at, created_by, updated_by)
SELECT account.id, encode(sha256(convert_to('demo-expired-session-' || account.id, 'UTF8')), 'hex'),
       TIMESTAMP '2026-11-10 09:00:00', account.id % 2 = 0,
       TIMESTAMP '2026-11-03 02:00:00', TIMESTAMP '2026-11-10 02:00:00', 'demo-seed', 'demo-seed'
FROM users account WHERE NOT account.is_deleted AND account.id % 37 = 0;
