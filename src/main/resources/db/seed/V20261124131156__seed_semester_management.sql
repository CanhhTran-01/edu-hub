-- Lịch sử học từ năm học 2017–2018 đến kỳ 1 năm học 2026–2027.
-- Hai kỳ kế hoạch năm học 2026–2027 phục vụ đăng ký đang mở và xếp lịch trước ngày đăng ký;
-- chỉ kế hoạch có ngày năm 2027, không tạo điểm hoặc sự kiện đã xảy ra sau 2026-11-24.
INSERT INTO semesters (semester_code, semester_name, start_date, end_date, registration_start, registration_end,
                       is_locked, created_at, updated_at, created_by, updated_by)
SELECT 'HK_' || semester_number || '_' || academic_year || '_' || (academic_year + 1),
       'Học kì ' || semester_number || ' năm học ' || academic_year || '-' || (academic_year + 1),
       CASE WHEN semester_number = 1 THEN make_date(academic_year, 8, 3) ELSE make_date(academic_year + 1, 1, 11) END,
       CASE WHEN semester_number = 1 THEN make_date(academic_year, 12, 31) ELSE make_date(academic_year + 1, 7, 3) END,
       CASE WHEN semester_number = 1 THEN make_date(academic_year, 7, 20)
            WHEN academic_year = 2026 THEN DATE '2026-11-16' ELSE make_date(academic_year + 1, 1, 4) END,
       CASE WHEN semester_number = 1 THEN make_date(academic_year, 7, 31)
            WHEN academic_year = 2026 THEN DATE '2026-12-04' ELSE make_date(academic_year + 1, 1, 9) END,
       academic_year < 2026, make_date(academic_year, 3, 2)::TIMESTAMP,
       make_date(academic_year, 3, 2)::TIMESTAMP, 'demo-seed', 'demo-seed'
FROM generate_series(2017, 2026) AS academic_year CROSS JOIN generate_series(1, 2) AS semester_number
ORDER BY academic_year, semester_number;

-- Kỳ hè chỉ mở hai học phần 30 tiết, đủ thời lượng xếp hai đợt trong tám tuần.
INSERT INTO semesters (semester_code, semester_name, start_date, end_date, registration_start, registration_end,
                       is_locked, created_at, updated_at, created_by, updated_by)
VALUES ('HK_3_2026_2027', 'Học kì 3 năm học 2026-2027', DATE '2027-07-05', DATE '2027-08-27',
        DATE '2027-06-21', DATE '2027-07-02', FALSE,
        TIMESTAMP '2026-11-23 01:00:00', TIMESTAMP '2026-11-23 01:00:00', 'demo-seed', 'demo-seed');
