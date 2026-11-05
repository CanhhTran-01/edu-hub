-- Dữ liệu mẫu cho môi trường phát triển: hai khoa và ba giảng viên, chưa có tài khoản người dùng.
-- Sequence mã giảng viên chính thức không bị tiêu tốn bởi các mã TEST-GV.
INSERT INTO faculties (faculty_name, created_at, updated_at, created_by, updated_by)
VALUES
    ('Công nghệ thông tin', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'),
    ('Điện tử viễn thông', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed');

INSERT INTO lecturers (
    faculty_id, lecturer_code, full_name, email, phone, status,
    created_at, updated_at, created_by, updated_by
)
SELECT faculties.id, seed.lecturer_code, seed.full_name, seed.email, seed.phone, seed.status,
       CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'seed', 'seed'
FROM (VALUES
    ('Công nghệ thông tin', 'TEST-GV001', 'Nguyễn Văn An', 'gv001@example.test', '0910000001', 'ACTIVE'),
    ('Công nghệ thông tin', 'TEST-GV002', 'Trần Thị Bình', 'gv002@example.test', '0910000002', 'INACTIVE'),
    ('Điện tử viễn thông', 'TEST-GV003', 'Lê Minh Cường', 'gv003@example.test', '0910000003', 'ACTIVE')
) AS seed(faculty_name, lecturer_code, full_name, email, phone, status)
JOIN faculties ON faculties.faculty_name = seed.faculty_name;
