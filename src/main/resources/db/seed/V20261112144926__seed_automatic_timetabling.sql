-- Bộ dữ liệu dev để thử UC-24 qua API: 8 môn, 18 lớp UNSCHEDULED, tổng cộng 277 ca cần xếp.
-- Kỳ HK_1_2028_2029 nằm sau bộ kỳ mẫu cũ; phải chạy xếp lịch trước ngày 01/08/2028.
-- Tra ID qua mã nghiệp vụ sau khi seed; không suy ID theo thứ tự INSERT.
-- Giữ nguyên dữ liệu đang có: không UPDATE/DELETE, không reset sequence hoặc tạo tài khoản/mật khẩu.
-- Chưa tạo ClassSchedule/TimetablePlan: người dùng tự chạy solver, xem và áp dụng phương án.
DO $seed_timetabling$
DECLARE
    demo_semester_id BIGINT;
    demo_faculty_id BIGINT;
    inserted_class_count INTEGER;
    seeded_at TIMESTAMP WITHOUT TIME ZONE := CURRENT_TIMESTAMP AT TIME ZONE 'UTC';
BEGIN
    -- Mã riêng tránh dùng nhầm dữ liệu thật. Nếu bị trùng thì dừng toàn bộ thay vì ghi đè hoặc bỏ qua.
    IF EXISTS (SELECT 1 FROM semesters WHERE semester_code = 'HK_1_2028_2029')
       OR EXISTS (SELECT 1 FROM faculties WHERE faculty_code = 'TTDEMO')
       OR EXISTS (SELECT 1 FROM subjects WHERE subject_code ~ '^TTDEMO0[1-8]$')
       OR EXISTS (SELECT 1 FROM lecturers WHERE lecturer_code ~ '^TEST-TT-GV0[1-8]$')
       OR EXISTS (SELECT 1 FROM rooms WHERE room_code ~ '^90[1-8]-TTDEMO$') THEN
        RAISE EXCEPTION 'Mã dữ liệu demo thời khóa biểu đã tồn tại; hãy đối soát, không ghi đè dữ liệu đã dùng';
    END IF;

    -- Không dời kỳ hiện hữu để nhường chỗ cho seed. PostgreSQL vẫn giữ exclusion constraint khi ghi cạnh tranh.
    IF EXISTS (
        SELECT 1 FROM semesters
        WHERE is_deleted = FALSE
          AND (daterange(start_date, end_date, '[]') && daterange(DATE '2028-08-11', DATE '2029-01-05', '[]')
            OR daterange(registration_start, registration_end, '[]') && daterange(DATE '2028-08-01', DATE '2028-08-10', '[]'))
    ) THEN
        RAISE EXCEPTION 'Kỳ demo thời khóa biểu trùng khoảng học hoặc đăng ký với kỳ hiện hữu; cần đối soát ngày trước khi seed';
    END IF;

    INSERT INTO semesters (
        semester_code, semester_name, start_date, end_date, registration_start, registration_end,
        is_locked, locked_grade, is_deleted, created_at, updated_at, created_by, updated_by
    ) VALUES (
        'HK_1_2028_2029', 'Học kì 1 năm học 2028-2029',
        DATE '2028-08-11', DATE '2029-01-05', DATE '2028-08-01', DATE '2028-08-10',
        FALSE, FALSE, FALSE, seeded_at, seeded_at, 'seed-timetabling', 'seed-timetabling'
    ) RETURNING id INTO demo_semester_id;

    -- Tổng tiết là dữ liệu mẫu khai báo riêng, không tính từ tín chỉ; 46/47 tiết đều cần 16 ca.
    INSERT INTO subjects (
        subject_code, subject_name, credits, total_teaching_periods, description, status,
        is_deleted, created_at, updated_at, created_by, updated_by
    )
    SELECT subject_code, subject_name, credits, total_teaching_periods,
           'Học phần demo dành cho kiểm tra xếp thời khóa biểu tự động', 'ACTIVE',
           FALSE, seeded_at, seeded_at, 'seed-timetabling', 'seed-timetabling'
    FROM (VALUES
        ('TTDEMO01', 'TT Demo - Lập trình cơ bản',          3, 45),
        ('TTDEMO02', 'TT Demo - Cơ sở dữ liệu',            3, 46),
        ('TTDEMO03', 'TT Demo - Mạng máy tính',            3, 47),
        ('TTDEMO04', 'TT Demo - Cấu trúc dữ liệu',         3, 48),
        ('TTDEMO05', 'TT Demo - Phát triển ứng dụng web',  4, 60),
        ('TTDEMO06', 'TT Demo - Kỹ năng làm việc nhóm',    2, 30),
        ('TTDEMO07', 'TT Demo - Kiểm thử phần mềm',        2, 36),
        ('TTDEMO08', 'TT Demo - Công nghệ phần mềm',       4, 54)
    ) AS demo_subjects(subject_code, subject_name, credits, total_teaching_periods);

    INSERT INTO faculties (
        faculty_name, faculty_code, active, is_deleted, created_at, updated_at, created_by, updated_by
    ) VALUES (
        'Khoa demo xếp thời khóa biểu', 'TTDEMO', TRUE, FALSE,
        seeded_at, seeded_at, 'seed-timetabling', 'seed-timetabling'
    ) RETURNING id INTO demo_faculty_id;

    -- Giảng viên chưa gắn User vẫn đủ điều kiện nếu hồ sơ ACTIVE và có môn có thể dạy.
    -- Mã TEST-TT-GV không dùng bộ đếm cấp MSGV chính thức; không tạo email/CCCD hoặc gửi thông báo.
    INSERT INTO lecturers (
        faculty_id, lecturer_code, full_name, recruitment_year, status, is_deleted,
        created_at, updated_at, created_by, updated_by
    )
    SELECT demo_faculty_id, lecturer_code, full_name, 2026, status, is_deleted,
           seeded_at, seeded_at, 'seed-timetabling', 'seed-timetabling'
    FROM (VALUES
        ('TEST-TT-GV01', 'Giảng viên demo xếp lịch 01', 'ACTIVE',   FALSE),
        ('TEST-TT-GV02', 'Giảng viên demo xếp lịch 02', 'ACTIVE',   FALSE),
        ('TEST-TT-GV03', 'Giảng viên demo xếp lịch 03', 'ACTIVE',   FALSE),
        ('TEST-TT-GV04', 'Giảng viên demo xếp lịch 04', 'ACTIVE',   FALSE),
        ('TEST-TT-GV05', 'Giảng viên demo xếp lịch 05', 'ACTIVE',   FALSE),
        ('TEST-TT-GV06', 'Giảng viên demo xếp lịch 06', 'ACTIVE',   FALSE),
        ('TEST-TT-GV07', 'Giảng viên demo đã ngừng dạy', 'INACTIVE', FALSE),
        ('TEST-TT-GV08', 'Giảng viên demo đã xóa',       'ACTIVE',   TRUE)
    ) AS demo_lecturers(lecturer_code, full_name, status, is_deleted);

    -- Mỗi môn có từ hai giảng viên hoạt động trở lên; hai hồ sơ bị loại cũng có liên kết môn để thử lọc.
    INSERT INTO lecturer_teaching_subjects (
        lecturer_id, subject_id, is_deleted, created_at, updated_at, created_by, updated_by
    )
    SELECT lecturers.id, subjects.id, FALSE, seeded_at, seeded_at, 'seed-timetabling', 'seed-timetabling'
    FROM (VALUES
        ('TEST-TT-GV01', ARRAY['TTDEMO01', 'TTDEMO02', 'TTDEMO07']),
        ('TEST-TT-GV02', ARRAY['TTDEMO01', 'TTDEMO03', 'TTDEMO08']),
        ('TEST-TT-GV03', ARRAY['TTDEMO02', 'TTDEMO04', 'TTDEMO05']),
        ('TEST-TT-GV04', ARRAY['TTDEMO03', 'TTDEMO05', 'TTDEMO06']),
        ('TEST-TT-GV05', ARRAY['TTDEMO04', 'TTDEMO06', 'TTDEMO07']),
        ('TEST-TT-GV06', ARRAY['TTDEMO05', 'TTDEMO07', 'TTDEMO08']),
        ('TEST-TT-GV07', ARRAY['TTDEMO01', 'TTDEMO02', 'TTDEMO03', 'TTDEMO04', 'TTDEMO05', 'TTDEMO06', 'TTDEMO07', 'TTDEMO08']),
        ('TEST-TT-GV08', ARRAY['TTDEMO01', 'TTDEMO02', 'TTDEMO03', 'TTDEMO04', 'TTDEMO05', 'TTDEMO06', 'TTDEMO07', 'TTDEMO08'])
    ) AS teaching_capabilities(lecturer_code, subject_codes)
    JOIN lecturers ON lecturers.lecturer_code = teaching_capabilities.lecturer_code
    CROSS JOIN LATERAL unnest(teaching_capabilities.subject_codes) AS teaching_subject(subject_code)
    JOIN subjects ON subjects.subject_code = teaching_subject.subject_code;

    -- Phòng 906 còn hoạt động nhưng không đủ chỗ cho các lớp demo; 907/908 đủ lớn nhưng không được chọn.
    -- Solver được dùng mọi phòng phù hợp trong database, không giới hạn riêng cơ sở DEMO-TT.
    INSERT INTO rooms (
        room_code, room_name, campus, building_code, room_number, capacity, status, is_deleted,
        created_at, updated_at, created_by, updated_by
    )
    SELECT room_number || '-TTDEMO', 'Phòng demo xếp lịch ' || room_number,
           'DEMO-TT', 'TTDEMO', room_number, capacity, status, is_deleted,
           seeded_at, seeded_at, 'seed-timetabling', 'seed-timetabling'
    FROM (VALUES
        ('901', 80,  'ACTIVE',   FALSE),
        ('902', 80,  'ACTIVE',   FALSE),
        ('903', 70,  'ACTIVE',   FALSE),
        ('904', 60,  'ACTIVE',   FALSE),
        ('905', 50,  'ACTIVE',   FALSE),
        ('906', 30,  'ACTIVE',   FALSE),
        ('907', 120, 'INACTIVE', FALSE),
        ('908', 120, 'ACTIVE',   TRUE)
    ) AS demo_rooms(room_number, capacity, status, is_deleted);

    -- Chốt số lớp và sức chứa trước khi xếp: hai môn đầu có ba lớp, sáu môn còn lại có hai lớp.
    -- Mã lớp dùng đúng công thức MC_<semesterId>_<subjectCode>_<classSequence>; ID do database cấp.
    INSERT INTO module_classes (
        semester_id, subject_id, lecturer_id, module_class_code, class_sequence,
        max_capacity, current_size, status, is_deleted, created_at, updated_at, created_by, updated_by
    )
    SELECT demo_semester_id, subjects.id, NULL,
           'MC_' || demo_semester_id || '_' || subjects.subject_code || '_' || lpad(class_position::TEXT, 3, '0'),
           class_position::INTEGER, max_capacity, 0, 'UNSCHEDULED', FALSE,
           seeded_at, seeded_at, 'seed-timetabling', 'seed-timetabling'
    FROM (VALUES
        ('TTDEMO01', ARRAY[80, 60, 40]),
        ('TTDEMO02', ARRAY[70, 50, 40]),
        ('TTDEMO03', ARRAY[80, 60]),
        ('TTDEMO04', ARRAY[70, 50]),
        ('TTDEMO05', ARRAY[80, 60]),
        ('TTDEMO06', ARRAY[60, 40]),
        ('TTDEMO07', ARRAY[50, 40]),
        ('TTDEMO08', ARRAY[80, 60])
    ) AS class_openings(subject_code, target_capacities)
    JOIN subjects ON subjects.subject_code = class_openings.subject_code
    CROSS JOIN LATERAL unnest(class_openings.target_capacities) WITH ORDINALITY AS class_capacities(max_capacity, class_position);

    GET DIAGNOSTICS inserted_class_count = ROW_COUNT;
    IF inserted_class_count <> 18 THEN
        RAISE EXCEPTION 'Bộ dữ liệu demo phải tạo đúng 18 lớp, thực tế %; hủy toàn bộ seed', inserted_class_count;
    END IF;
END;
$seed_timetabling$;
