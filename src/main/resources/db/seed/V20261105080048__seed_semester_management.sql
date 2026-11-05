-- Database tự sinh ID; mã, tên và các ngày được ghi rõ trong từng bản ghi VALUES.
-- Các khoảng học và đăng ký không chồng lấn, ngày đóng đăng ký luôn trước ngày bắt đầu kỳ.

-- Mỗi năm học từ 2022-2023 đến 2027-2028 có đúng 2 kỳ.
-- Kỳ 1: 11/08 năm nay - 05/01 năm sau.
-- Kỳ 2: 11/01 năm sau - 05/07 năm sau.
INSERT INTO semesters (
    semester_code, semester_name, start_date, end_date, registration_start, registration_end,
    is_locked, locked_grade, is_deleted, created_at, updated_at, created_by, updated_by
)
VALUES
    -- Năm học 2022-2023
    (
        'HK_1_2022_2023', 'Học kì 1 năm học 2022-2023',
        DATE '2022-08-11', DATE '2023-01-05', DATE '2022-08-01', DATE '2022-08-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    ),
    (
        'HK_2_2022_2023', 'Học kì 2 năm học 2022-2023',
        DATE '2023-01-11', DATE '2023-07-05', DATE '2023-01-06', DATE '2023-01-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    ),
    -- Năm học 2023-2024
    (
        'HK_1_2023_2024', 'Học kì 1 năm học 2023-2024',
        DATE '2023-08-11', DATE '2024-01-05', DATE '2023-08-01', DATE '2023-08-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    ),
    (
        'HK_2_2023_2024', 'Học kì 2 năm học 2023-2024',
        DATE '2024-01-11', DATE '2024-07-05', DATE '2024-01-06', DATE '2024-01-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    ),
    -- Năm học 2024-2025
    (
        'HK_1_2024_2025', 'Học kì 1 năm học 2024-2025',
        DATE '2024-08-11', DATE '2025-01-05', DATE '2024-08-01', DATE '2024-08-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    ),
    (
        'HK_2_2024_2025', 'Học kì 2 năm học 2024-2025',
        DATE '2025-01-11', DATE '2025-07-05', DATE '2025-01-06', DATE '2025-01-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    ),
    -- Năm học 2025-2026
    (
        'HK_1_2025_2026', 'Học kì 1 năm học 2025-2026',
        DATE '2025-08-11', DATE '2026-01-05', DATE '2025-08-01', DATE '2025-08-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    ),
    (
        'HK_2_2025_2026', 'Học kì 2 năm học 2025-2026',
        DATE '2026-01-11', DATE '2026-07-05', DATE '2026-01-06', DATE '2026-01-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    ),
    -- Năm học 2026-2027
    (
        'HK_1_2026_2027', 'Học kì 1 năm học 2026-2027',
        DATE '2026-08-11', DATE '2027-01-05', DATE '2026-08-01', DATE '2026-08-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    ),
    (
        'HK_2_2026_2027', 'Học kì 2 năm học 2026-2027',
        DATE '2027-01-11', DATE '2027-07-05', DATE '2027-01-06', DATE '2027-01-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    ),
    -- Năm học 2027-2028
    (
        'HK_1_2027_2028', 'Học kì 1 năm học 2027-2028',
        DATE '2027-08-11', DATE '2028-01-05', DATE '2027-08-01', DATE '2027-08-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    ),
    (
        'HK_2_2027_2028', 'Học kì 2 năm học 2027-2028',
        DATE '2028-01-11', DATE '2028-07-05', DATE '2028-01-06', DATE '2028-01-10',
        FALSE, FALSE, FALSE,
        CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
    );
