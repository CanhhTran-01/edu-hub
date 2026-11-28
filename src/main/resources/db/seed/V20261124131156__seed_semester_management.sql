-- Mỗi năm học từ 2020–2021 đến 2026–2027 có đúng hai kỳ, không tạo kỳ hè.
-- Kỳ 1 năm học 2026–2027 phục vụ What-If; kỳ 2 chỉ có metadata kế hoạch, chưa có lớp hoặc lượt học.
INSERT INTO semesters (semester_code, semester_name, start_date, end_date, registration_start, registration_end,
                       is_locked, created_at, updated_at, created_by, updated_by)
SELECT 'HK_' || semester_number || '_' || academic_year || '_' || (academic_year + 1),
       'Học kì ' || semester_number || ' năm học ' || academic_year || '-' || (academic_year + 1),
       CASE WHEN semester_number = 1 THEN make_date(academic_year, 8, 3) ELSE make_date(academic_year + 1, 1, 11) END,
       CASE WHEN semester_number = 1 THEN make_date(academic_year, 12, 31) ELSE make_date(academic_year + 1, 7, 3) END,
       CASE WHEN semester_number = 1 THEN make_date(academic_year, 7, 20) ELSE make_date(academic_year + 1, 1, 4) END,
       CASE WHEN semester_number = 1 THEN make_date(academic_year, 7, 31) ELSE make_date(academic_year + 1, 1, 9) END,
       academic_year < 2026, make_date(academic_year, 3, 2)::TIMESTAMP,
       make_date(academic_year, 3, 2)::TIMESTAMP, 'demo-seed', 'demo-seed'
FROM generate_series(2020, 2026) AS academic_year CROSS JOIN generate_series(1, 2) AS semester_number
ORDER BY academic_year, semester_number;
