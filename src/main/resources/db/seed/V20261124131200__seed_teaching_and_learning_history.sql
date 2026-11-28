-- Lớp, lịch và lượt học được tạo cùng transaction để dùng một ánh xạ lớp hành chính–lớp học phần.
-- Lịch sử chung đến hết kỳ 2 năm học 2025–2026; kỳ 1 năm học 2026–2027 chỉ thêm chín môn cho student khóa 2023.
-- Mỗi lớp phục vụ một lớp hành chính; lớp học lại được mở riêng, không ghép trùng lịch của người học.
CREATE TEMP TABLE demo_class_openings ON COMMIT DROP AS
WITH academic_paths AS (
    SELECT administrative_class.id AS administrative_class_id, curriculum_subject.subject_id,
           cohort.start_year + (curriculum_subject.recommend_semester - 1) / 2 AS academic_year,
           1 + ((curriculum_subject.recommend_semester - 1) % 2) AS semester_number
    FROM administrative_classes administrative_class JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id
    JOIN curriculums curriculum ON curriculum.major_id = administrative_class.major_id
        AND curriculum.cohort_id = cohort.id AND curriculum.training_type = administrative_class.training_type
    JOIN curriculum_subjects curriculum_subject ON curriculum_subject.curriculum_id = curriculum.id
    UNION
    -- Học lại hai môn không là tiên quyết; giữ lần F/DROPPED cũ và mở lượt mới ở kỳ 3 lộ trình.
    SELECT administrative_class.id, subject.id, cohort.start_year + 1, 1
    FROM administrative_classes administrative_class JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id
    CROSS JOIN subjects subject WHERE subject.subject_code IN ('GEN101', 'GEN102')
    UNION
    -- Học bù môn đã bỏ lỡ ở kỳ 2, không dựng kết quả đạt trước khi thực sự học bù.
    SELECT administrative_class.id, subject.id, cohort.start_year + 1, 2
    FROM administrative_classes administrative_class JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id
    CROSS JOIN subjects subject WHERE subject.subject_code = 'GEN103'
), semester_openings AS (
    SELECT academic_paths.*, semester.id AS semester_id, semester.start_date, semester.end_date,
           row_number() OVER (PARTITION BY administrative_class_id, semester.id ORDER BY subject.subject_code)::INTEGER - 1 AS pattern_order
    FROM academic_paths JOIN subjects subject ON subject.id = academic_paths.subject_id
    JOIN semesters semester ON semester.semester_code = 'HK_' || academic_paths.semester_number || '_'
        || academic_paths.academic_year || '_' || (academic_paths.academic_year + 1)
    WHERE academic_paths.academic_year <= 2025
), numbered_openings AS (
    SELECT semester_openings.*,
           row_number() OVER (PARTITION BY semester_id, subject_id ORDER BY administrative_class_id)::INTEGER AS class_sequence
    FROM semester_openings
)
SELECT numbered_openings.*, 'MC_' || semester_id || '_' || subject.subject_code || '_'
       || lpad(class_sequence::TEXT, 3, '0') AS module_class_code,
       ((pattern_order + administrative_class_id) % 8)::INTEGER AS pattern_number
FROM numbered_openings JOIN subjects subject ON subject.id = numbered_openings.subject_id;

-- Dùng sinh viên khóa cũ để demo kỳ hiện tại; không tạo khóa nhập học 2026 hoặc phương án nhập học mới.
CREATE TEMP TABLE demo_what_if_student ON COMMIT DROP AS
SELECT student.id AS student_id, student.class_id AS administrative_class_id
FROM students student JOIN users account ON account.id = student.user_id
JOIN roles role ON role.id = account.role_id
JOIN administrative_classes administrative_class ON administrative_class.id = student.class_id
JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id
WHERE account.username = 'student' AND account.status = 'ACTIVE' AND NOT account.is_first_login
    AND NOT account.is_deleted AND NOT student.is_deleted AND student.activity_status = 'STUDYING'
    AND role.role_name = 'STUDENT' AND cohort.start_year = 2023;

CREATE TEMP TABLE demo_what_if_courses (
    course_position INTEGER PRIMARY KEY, subject_code TEXT NOT NULL UNIQUE,
    published_course_score NUMERIC(3,1)
) ON COMMIT DROP;
INSERT INTO demo_what_if_courses VALUES
    (1, 'IT401', 7.0), (2, 'IT402', 8.0), (3, 'IT403', 8.5), (4, 'IT404', 8.0),
    (5, 'IT405', NULL), (6, 'IT406', NULL), (7, 'IT407', NULL), (8, 'IT408', NULL), (9, 'IT409', NULL);

-- Các môn thuộc CTĐT của student; học trước môn kỳ 8 được phép khi đã đủ tiên quyết, không đổi lộ trình CTĐT.
WITH what_if_openings AS (
    SELECT demo_student.administrative_class_id, subject.id AS subject_id, semester.id AS semester_id,
           semester.start_date, semester.end_date, demo_course.course_position, 1 AS class_sequence
    FROM demo_what_if_student demo_student CROSS JOIN demo_what_if_courses demo_course
    JOIN subjects subject ON subject.subject_code = demo_course.subject_code
        AND subject.status = 'ACTIVE' AND NOT subject.is_deleted
    JOIN administrative_classes administrative_class ON administrative_class.id = demo_student.administrative_class_id
    JOIN curriculums curriculum ON curriculum.major_id = administrative_class.major_id
        AND curriculum.cohort_id = administrative_class.cohort_id
        AND curriculum.training_type = administrative_class.training_type
    JOIN curriculum_subjects curriculum_subject ON curriculum_subject.curriculum_id = curriculum.id
        AND curriculum_subject.subject_id = subject.id AND NOT curriculum_subject.is_deleted
    JOIN semesters semester ON semester.semester_code = 'HK_1_2026_2027'
)
INSERT INTO demo_class_openings (administrative_class_id, subject_id, academic_year, semester_number,
                                 semester_id, start_date, end_date, pattern_order, class_sequence,
                                 module_class_code, pattern_number)
SELECT administrative_class_id, subject_id, 2026, 1, semester_id, start_date, end_date,
       course_position - 1, class_sequence,
       'MC_' || semester_id || '_' || subject.subject_code || '_' || lpad(class_sequence::TEXT, 3, '0'),
       CASE WHEN course_position <= 5 THEN course_position - 1 ELSE course_position - 6 END
FROM what_if_openings JOIN subjects subject ON subject.id = what_if_openings.subject_id;

DO $$ BEGIN
    IF (SELECT count(*) FROM demo_what_if_student) <> 1
       OR (SELECT count(*) FROM demo_class_openings WHERE academic_year = 2026) <> 9
    THEN RAISE EXCEPTION 'Demo What-If phải có đúng student khóa 2023 và chín học phần thuộc CTĐT'; END IF;
END $$;

-- Các bảng tạm được tra lặp theo lớp/kỳ; index và thống kê chỉ tồn tại trong transaction seed.
CREATE UNIQUE INDEX ON demo_class_openings (administrative_class_id, subject_id, academic_year, semester_number);
CREATE UNIQUE INDEX ON demo_class_openings (module_class_code);
ANALYZE demo_class_openings;

CREATE TEMP TABLE demo_subject_phases ON COMMIT DROP AS
WITH ranked_subjects AS (
    SELECT semester_id, subject_id, row_number() OVER (PARTITION BY semester_id ORDER BY subject_id) AS subject_position,
           count(*) OVER (PARTITION BY semester_id) AS subject_count
    FROM (SELECT DISTINCT semester_id, subject_id FROM demo_class_openings) semester_subjects
)
SELECT semester_id, subject_id, CASE WHEN subject_position <= (subject_count + 1) / 2 THEN 1 ELSE 2 END AS phase
FROM ranked_subjects;

-- Lịch sử dùng ba ca/tuần: Hai/Tư/Sáu hoặc Ba/Năm/Bảy, tối đa bảy môn/kỳ.
-- Chín môn What-If dùng hai ca/tuần và mẫu riêng trong từng đợt: bốn môn đã có điểm, năm môn còn chờ công bố.
CREATE TEMP TABLE demo_schedule_requirements ON COMMIT DROP AS
WITH resource_requirements AS (
    SELECT opening.*, subject.subject_code, subject.total_teaching_periods, phases.phase,
           CASE WHEN opening.academic_year = 2026 THEN 2 ELSE 3 END AS sessions_per_week,
           CASE WHEN subject.subject_code ~ '^(GEN|MATH|SEM)' THEN 'KHCB'
                WHEN subject.subject_code ~ '^SEC' THEN 'ATTT' WHEN subject.subject_code ~ '^ET' THEN 'DTVT'
                WHEN subject.subject_code ~ '^AI' OR subject.subject_code = 'CS110' THEN 'TTNT' ELSE 'CNTT' END AS faculty_code
    FROM demo_class_openings opening JOIN subjects subject ON subject.id = opening.subject_id
    JOIN demo_subject_phases phases ON phases.semester_id = opening.semester_id AND phases.subject_id = opening.subject_id
)
SELECT resource_requirements.*,
       row_number() OVER (PARTITION BY semester_id, phase, pattern_number ORDER BY module_class_code)::INTEGER AS room_position,
       row_number() OVER (PARTITION BY semester_id, phase, pattern_number, faculty_code ORDER BY module_class_code)::INTEGER AS lecturer_position
FROM resource_requirements;

CREATE TEMP TABLE demo_room_choices ON COMMIT DROP AS
SELECT id AS room_id, row_number() OVER (ORDER BY room_code)::INTEGER AS room_position
FROM rooms WHERE status = 'ACTIVE' AND NOT is_deleted AND capacity >= 50;
CREATE TEMP TABLE demo_lecturer_choices ON COMMIT DROP AS
SELECT lecturer.id AS lecturer_id, faculty.faculty_code,
       row_number() OVER (PARTITION BY faculty.id ORDER BY lecturer.lecturer_code)::INTEGER AS lecturer_position,
       count(*) OVER (PARTITION BY faculty.id)::INTEGER AS eligible_lecturer_count
FROM lecturers lecturer JOIN faculties faculty ON faculty.id = lecturer.faculty_id
WHERE lecturer.status = 'ACTIVE' AND NOT lecturer.is_deleted;

-- Dừng khi thiếu tài nguyên thay vì bỏ lớp hoặc gán trùng phòng/giảng viên.
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM demo_schedule_requirements requirement
        LEFT JOIN demo_room_choices room_choice USING (room_position)
        LEFT JOIN demo_lecturer_choices lecturer_choice USING (faculty_code, lecturer_position)
        WHERE room_choice.room_id IS NULL OR lecturer_choice.lecturer_id IS NULL
    ) THEN RAISE EXCEPTION 'Bộ seed thiếu phòng hoặc giảng viên cho các mẫu lịch độc lập'; END IF;
END $$;

CREATE TEMP TABLE demo_semester_phases ON COMMIT DROP AS
WITH phase_lengths AS (
    SELECT requirement.semester_id,
           (semester.start_date + ((8 - extract(isodow FROM semester.start_date)::INTEGER) % 7)) AS phase_one_start,
           max(((requirement.total_teaching_periods + 2) / 3 + requirement.sessions_per_week - 1)
               / requirement.sessions_per_week) FILTER (WHERE requirement.phase = 1) AS phase_one_weeks
    FROM demo_schedule_requirements requirement JOIN semesters semester ON semester.id = requirement.semester_id
    GROUP BY requirement.semester_id, semester.start_date
)
SELECT semester_id, phase_one_start, phase_one_start + phase_one_weeks * 7 - 1 AS phase_one_end,
       phase_one_start + (phase_one_weeks + 2) * 7 AS phase_two_start
FROM phase_lengths;

CREATE TEMP TABLE demo_session_placements ON COMMIT DROP AS
SELECT requirement.module_class_code, requirement.administrative_class_id, requirement.semester_id,
       requirement.subject_id, requirement.class_sequence, requirement.phase, session_index,
       room_choice.room_id, lecturer_choice.lecturer_id,
       (CASE WHEN requirement.phase = 1 THEN semester_phase.phase_one_start ELSE semester_phase.phase_two_start END
        + (session_index / requirement.sessions_per_week) * 7
        + (session_index % requirement.sessions_per_week) * CASE WHEN requirement.sessions_per_week = 2 THEN 3 ELSE 2 END
        + (requirement.pattern_number / 4)) AS teaching_date,
       (requirement.pattern_number % 4) + 1 AS shift
FROM demo_schedule_requirements requirement
JOIN demo_room_choices room_choice USING (room_position)
-- Xoay vị trí theo mẫu ca để san tải, tránh giao mọi mẫu trong tuần cho cùng giảng viên đầu danh sách.
JOIN demo_lecturer_choices lecturer_choice ON lecturer_choice.faculty_code = requirement.faculty_code
    AND lecturer_choice.lecturer_position = 1 + ((requirement.lecturer_position - 1 + requirement.pattern_number * 3)
        % lecturer_choice.eligible_lecturer_count)
JOIN demo_semester_phases semester_phase ON semester_phase.semester_id = requirement.semester_id
CROSS JOIN LATERAL generate_series(0, (requirement.total_teaching_periods + 2) / 3 - 1) AS session_index;

CREATE INDEX ON demo_session_placements (semester_id);
CREATE INDEX ON demo_session_placements (module_class_code);
ANALYZE demo_session_placements;

INSERT INTO module_classes (module_class_code, semester_id, subject_id, lecturer_id, class_sequence,
                           max_capacity, current_size, status, created_at, updated_at, created_by, updated_by)
SELECT placement.module_class_code, placement.semester_id, placement.subject_id, placement.lecturer_id,
       placement.class_sequence, 50, 0,
       CASE WHEN max(placement.teaching_date) < DATE '2026-11-28' THEN 'COMPLETED' ELSE 'CLOSED' END,
       semester.registration_start - INTERVAL '7 days',
       TIMESTAMP '2026-11-23 02:00:00', 'demo-seed', 'demo-seed'
FROM demo_session_placements placement JOIN semesters semester ON semester.id = placement.semester_id
GROUP BY placement.module_class_code, placement.semester_id, placement.subject_id, placement.lecturer_id,
         placement.class_sequence, placement.administrative_class_id, semester.start_date, semester.registration_start
ORDER BY placement.semester_id, placement.subject_id, placement.class_sequence;

INSERT INTO class_schedules (module_class_id, room_id, day_of_week, lesson_order_start, lesson_order_end,
                            start_date, end_date, created_at, updated_at, created_by, updated_by)
SELECT module_class.id, placement.room_id, extract(isodow FROM placement.teaching_date)::INTEGER + 1,
       (placement.shift - 1) * 3 + 1, placement.shift * 3, placement.teaching_date, placement.teaching_date,
       semester.registration_start - INTERVAL '2 days' + INTERVAL '2 hours',
       semester.registration_start - INTERVAL '2 days' + INTERVAL '2 hours',
       'demo-seed', 'demo-seed'
FROM demo_session_placements placement JOIN module_classes module_class ON module_class.module_class_code = placement.module_class_code
JOIN semesters semester ON semester.id = module_class.semester_id
ORDER BY module_class.id, placement.session_index;

-- Lưu bản chụp APPLIED đúng cấu trúc codec để lịch chính thức đọc được hai đợt.
-- Không tạo worker đang chạy, không seed phương án FEASIBLE có fingerprint không thể áp dụng.
WITH lecturer_session_counts AS (
    SELECT semester_id, lecturer_id, count(*) AS session_count
    FROM demo_session_placements GROUP BY semester_id, lecturer_id
), lecturer_load_penalties AS (
    SELECT semester_id, trunc(sum(session_count * session_count)::NUMERIC / sum(session_count))::BIGINT AS workload_penalty
    FROM lecturer_session_counts GROUP BY semester_id
), lecturer_shifts AS (
    SELECT DISTINCT semester_id, lecturer_id, teaching_date, shift FROM demo_session_placements
), lecturer_shift_gaps AS (
    SELECT semester_id, shift,
           lag(shift) OVER (PARTITION BY semester_id, lecturer_id, teaching_date ORDER BY shift) AS previous_shift
    FROM lecturer_shifts
), lecturer_gap_penalties AS (
    SELECT semester_id, sum(greatest(shift - previous_shift - 1, 0))::BIGINT AS shift_gap_penalty
    FROM lecturer_shift_gaps GROUP BY semester_id
), plan_snapshots AS (
    SELECT semester.id AS semester_id, semester.registration_start,
           semester_phase.phase_one_start, semester_phase.phase_one_end, semester_phase.phase_two_start,
           jsonb_build_object(
               'semesterId', semester.id, 'startDate', semester.start_date, 'endDate', semester.end_date,
               'registrationStart', semester.registration_start, 'registrationEnd', semester.registration_end,
               'classes', (SELECT jsonb_agg(jsonb_build_object(
                   'moduleClassId', module_class.id, 'moduleClassCode', module_class.module_class_code,
                   'subjectId', subject.id, 'subjectCode', subject.subject_code,
                   'totalTeachingPeriods', subject.total_teaching_periods, 'maxCapacity', module_class.max_capacity,
                   'currentSize', 0, 'status', 'UNSCHEDULED', 'lecturerId', NULL) ORDER BY module_class.id)
                   FROM module_classes module_class JOIN subjects subject ON subject.id = module_class.subject_id
                   WHERE module_class.semester_id = semester.id),
               'lecturers', (SELECT jsonb_agg(jsonb_build_object('lecturerId', lecturer.id, 'fullName', lecturer.full_name,
                   'teachingSubjectIds', (SELECT jsonb_agg(teaching_subject.subject_id ORDER BY teaching_subject.subject_id)
                       FROM lecturer_teaching_subjects teaching_subject WHERE teaching_subject.lecturer_id = lecturer.id)) ORDER BY lecturer.id)
                   FROM lecturers lecturer WHERE lecturer.status = 'ACTIVE' AND NOT lecturer.is_deleted
                       AND lecturer.created_at <= semester.registration_start::TIMESTAMP),
               'rooms', (SELECT jsonb_agg(jsonb_build_object('roomId', room.id, 'roomCode', room.room_code, 'capacity', room.capacity) ORDER BY room.id)
                   FROM rooms room WHERE room.status = 'ACTIVE' AND NOT room.is_deleted),
               'existingSessions', '[]'::JSONB, 'replacedSessions', '[]'::JSONB,
               'pinnedModuleClassIds', '[]'::JSONB, 'previousAllocation', NULL) AS problem,
           jsonb_build_object('phaseOneStart', semester_phase.phase_one_start, 'phaseOneEnd', semester_phase.phase_one_end,
               'phaseTwoStart', semester_phase.phase_two_start,
               'subjects', (SELECT jsonb_agg(jsonb_build_object('subjectId', phases.subject_id, 'phase', phases.phase) ORDER BY phases.subject_id)
                   FROM demo_subject_phases phases WHERE phases.semester_id = semester.id),
               'sessions', (SELECT jsonb_agg(jsonb_build_object('moduleClassId', module_class.id, 'sessionIndex', placement.session_index,
                   'lecturerId', placement.lecturer_id, 'roomId', placement.room_id, 'date', placement.teaching_date, 'shift', placement.shift)
                   ORDER BY module_class.id, placement.session_index)
                   FROM demo_session_placements placement JOIN module_classes module_class ON module_class.module_class_code = placement.module_class_code
                   WHERE placement.semester_id = semester.id)) AS allocation
    FROM semesters semester JOIN demo_semester_phases semester_phase ON semester_phase.semester_id = semester.id
)
INSERT INTO timetable_plans (semester_id, revision, state, input_fingerprint, problem_json, allocation_json,
                            diagnostics_json, hard_penalty, soft_penalty, expires_at, applied_at,
                            created_at, updated_at, created_by, updated_by)
SELECT semester_id, 1, 'APPLIED', encode(sha256(convert_to(problem::TEXT, 'UTF8')), 'hex'), problem::TEXT, allocation::TEXT,
       '[]', 0, workload_penalty + shift_gap_penalty, registration_start - INTERVAL '1 day' + INTERVAL '9 hours',
       registration_start - INTERVAL '2 days' + INTERVAL '9 hours',
       registration_start - INTERVAL '3 days' + INTERVAL '2 hours',
       registration_start - INTERVAL '2 days' + INTERVAL '2 hours', 'demo-seed', 'demo-seed'
FROM plan_snapshots JOIN lecturer_load_penalties USING (semester_id) JOIN lecturer_gap_penalties USING (semester_id);

-- Giữ một mã lớp đã hủy để thử tìm kiếm/lịch sử và việc không tái sử dụng STT.
INSERT INTO module_classes (module_class_code, semester_id, subject_id, class_sequence, max_capacity, current_size,
                           status, created_at, updated_at, created_by, updated_by)
SELECT 'MC_' || semester.id || '_' || subject.subject_code || '_999', semester.id, subject.id, 999, 50, 0,
       'CANCELLED', semester.registration_start - INTERVAL '7 days' + INTERVAL '2 hours',
       semester.registration_start - INTERVAL '6 days' + INTERVAL '2 hours', 'demo-seed', 'demo-seed'
FROM semesters semester CROSS JOIN subjects subject
WHERE semester.semester_code = 'HK_2_2025_2026' AND subject.subject_code = 'SEM101';

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM class_schedules schedule JOIN module_classes module_class ON module_class.id = schedule.module_class_id
               JOIN semesters semester ON semester.id = module_class.semester_id
               WHERE schedule.start_date < semester.start_date OR schedule.end_date > semester.end_date)
    THEN RAISE EXCEPTION 'Bộ seed có buổi học ngoài khoảng ngày của kỳ'; END IF;
    IF EXISTS (SELECT 1 FROM class_schedules GROUP BY room_id, start_date, lesson_order_start HAVING count(*) > 1)
       OR EXISTS (SELECT 1 FROM class_schedules schedule JOIN module_classes module_class ON module_class.id = schedule.module_class_id
                  GROUP BY module_class.lecturer_id, schedule.start_date, schedule.lesson_order_start HAVING count(*) > 1)
    THEN RAISE EXCEPTION 'Bộ seed có lịch trùng phòng hoặc giảng viên'; END IF;
END $$;

-- Ngày học cuối và số ca là thuộc tính của lớp: tổng hợp một lần, dùng lại cho lượt học và kiểm tra đủ lịch.
CREATE TEMP TABLE demo_class_schedule_summaries ON COMMIT DROP AS
SELECT module_class_id, max(end_date) AS last_teaching_date, count(*) AS session_count
FROM class_schedules WHERE NOT is_deleted GROUP BY module_class_id;
CREATE UNIQUE INDEX ON demo_class_schedule_summaries (module_class_id);
ANALYZE demo_class_schedule_summaries;

-- Dựng từng lượt học theo lớp vừa mở; không đổi kết quả cũ khi học lại hoặc hủy đăng ký.
CREATE TEMP TABLE demo_learning_attempts ON COMMIT DROP AS
WITH planned_attempts AS (
    SELECT student.id AS student_id, student.student_code, student.is_deleted AS student_deleted,
           student.created_at AS student_created_at, account.username,
           right(student.student_code, 2)::INTEGER AS student_sequence,
           curriculum_subject.subject_id, curriculum_subject.recommend_semester,
           administrative_class.id AS administrative_class_id,
           cohort.start_year + (curriculum_subject.recommend_semester - 1) / 2 AS academic_year,
           1 + ((curriculum_subject.recommend_semester - 1) % 2) AS semester_number,
           FALSE AS is_retake
    FROM students student JOIN users account ON account.id = student.user_id
    JOIN administrative_classes administrative_class ON administrative_class.id = student.class_id
    JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id
    JOIN curriculums curriculum ON curriculum.major_id = administrative_class.major_id
        AND curriculum.cohort_id = cohort.id AND curriculum.training_type = administrative_class.training_type
    JOIN curriculum_subjects curriculum_subject ON curriculum_subject.curriculum_id = curriculum.id
    WHERE cohort.start_year + (curriculum_subject.recommend_semester - 1) / 2 <= 2025
    UNION ALL
    SELECT student.id, student.student_code, student.is_deleted, student.created_at, account.username,
           right(student.student_code, 2)::INTEGER, subject.id, 3, administrative_class.id, cohort.start_year + 1, 1, TRUE
    FROM students student JOIN users account ON account.id = student.user_id
    JOIN administrative_classes administrative_class ON administrative_class.id = student.class_id
    JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id CROSS JOIN subjects subject
    WHERE (subject.subject_code = 'GEN101' AND (right(student.student_code, 2)::INTEGER % 13 = 0 OR account.username = 'student'))
       OR (subject.subject_code = 'GEN102' AND (right(student.student_code, 2)::INTEGER % 17 = 0 OR account.username = 'student'))
    UNION ALL
    SELECT student.id, student.student_code, student.is_deleted, student.created_at, account.username,
           right(student.student_code, 2)::INTEGER, subject.id, 4, administrative_class.id, cohort.start_year + 1, 2, TRUE
    FROM students student JOIN users account ON account.id = student.user_id
    JOIN administrative_classes administrative_class ON administrative_class.id = student.class_id
    JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id CROSS JOIN subjects subject
    WHERE subject.subject_code = 'GEN103' AND right(student.student_code, 2)::INTEGER % 29 = 0
    UNION ALL
    -- Chỉ student nhận đủ chín môn demo; các môn kỳ 8 được học trước trong cùng CTĐT, không tạo lượt trùng.
    SELECT student.id, student.student_code, student.is_deleted, student.created_at, account.username,
           right(student.student_code, 2)::INTEGER, subject.id, curriculum_subject.recommend_semester,
           demo_student.administrative_class_id, 2026, 1, FALSE
    FROM demo_what_if_student demo_student JOIN students student ON student.id = demo_student.student_id
    JOIN users account ON account.id = student.user_id CROSS JOIN demo_what_if_courses demo_course
    JOIN subjects subject ON subject.subject_code = demo_course.subject_code
    JOIN administrative_classes administrative_class ON administrative_class.id = demo_student.administrative_class_id
    JOIN curriculums curriculum ON curriculum.major_id = administrative_class.major_id
        AND curriculum.cohort_id = administrative_class.cohort_id
        AND curriculum.training_type = administrative_class.training_type
    JOIN curriculum_subjects curriculum_subject ON curriculum_subject.curriculum_id = curriculum.id
        AND curriculum_subject.subject_id = subject.id AND NOT curriculum_subject.is_deleted
), matched_attempts AS (
    SELECT attempt.*, module_class.id AS module_class_id, module_class.module_class_code,
           semester.id AS semester_id, semester.start_date, semester.registration_start,
           subject.subject_code, subject.subject_name, subject.credits, schedule_summary.last_teaching_date
    FROM planned_attempts attempt JOIN subjects subject ON subject.id = attempt.subject_id
    JOIN demo_class_openings opening ON opening.administrative_class_id = attempt.administrative_class_id
        AND opening.subject_id = attempt.subject_id AND opening.academic_year = attempt.academic_year
        AND opening.semester_number = attempt.semester_number
    JOIN module_classes module_class ON module_class.module_class_code = opening.module_class_code
    JOIN semesters semester ON semester.id = module_class.semester_id
    JOIN demo_class_schedule_summaries schedule_summary ON schedule_summary.module_class_id = module_class.id
    -- Môn bỏ lỡ không có lượt học kỳ gốc; học bù chỉ xuất hiện ở kỳ 4.
    WHERE NOT (subject.subject_code = 'GEN103' AND NOT attempt.is_retake AND attempt.student_sequence % 29 = 0)
), attempt_outcomes AS (
    SELECT matched_attempts.*, what_if_course.published_course_score AS demo_course_score,
           matched_attempts.subject_code = 'GEN102' AND NOT is_retake AND (student_sequence % 17 = 0 OR username = 'student') AS is_dropped,
           matched_attempts.subject_code = 'GEN101' AND NOT is_retake AND (student_sequence % 13 = 0 OR username = 'student') AS is_failed,
           -- Kỳ demo chỉ công bố bốn môn đã hoàn tất; năm môn còn lại giữ điểm và mốc công bố null cho simulator.
           -- Lịch sử đến kỳ 2 năm học 2025–2026 vẫn giữ nhóm chờ thi bổ sung như dữ liệu đã chốt.
           CASE WHEN academic_year = 2026 THEN what_if_course.published_course_score IS NOT NULL
                ELSE last_teaching_date + 3 <= DATE '2026-07-03'
                    AND NOT (academic_year = 2025 AND semester_number = 2
                        AND matched_attempts.subject_code = 'MATH101' AND student_sequence % 11 = 0) END AS has_published_grade,
           CASE WHEN matched_attempts.subject_code = 'CS101' AND academic_year <= 2022 THEN 2 ELSE credits END AS snapshot_credits
    FROM matched_attempts
    LEFT JOIN demo_what_if_courses what_if_course ON academic_year = 2026
        AND what_if_course.subject_code = matched_attempts.subject_code
)
SELECT attempt_outcomes.*,
       CASE WHEN academic_year = 2026 THEN demo_course_score
            WHEN is_failed THEN 3.0
            WHEN student_sequence % 31 = 0 THEN 0.0
            WHEN student_sequence % 7 = 0 AND NOT has_published_grade THEN NULL
            ELSE 6 + ((student_id + subject_id) % 36)::NUMERIC / 10 END AS component_score_1,
       CASE WHEN academic_year = 2026 THEN demo_course_score
            WHEN is_failed THEN 2.0
            WHEN student_sequence % 3 = 0 AND NOT has_published_grade THEN NULL
            ELSE 6 + ((student_id * 3 + subject_id) % 41)::NUMERIC / 10 END AS component_score_2,
       CASE WHEN academic_year = 2026 THEN demo_course_score
            WHEN NOT has_published_grade OR is_dropped THEN NULL WHEN is_failed THEN 2.0
            ELSE 5.5 + ((student_id * 7 + subject_id) % 46)::NUMERIC / 10 END AS final_exam_score
FROM attempt_outcomes;

WITH process_scores AS (
    SELECT attempt.*, CASE WHEN component_score_1 IS NOT NULL AND component_score_2 IS NOT NULL AND NOT is_dropped
                           THEN round(0.3 * component_score_1 + 0.7 * component_score_2, 1) END AS process_score
    FROM demo_learning_attempts attempt
), course_scores AS (
    SELECT process_scores.*, CASE WHEN final_exam_score IS NOT NULL
                                 THEN round(0.3 * process_score + 0.7 * final_exam_score, 1) END AS course_score
    FROM process_scores
)
INSERT INTO enrollments (student_id, module_class_id, subject_id, semester_id, subject_code, subject_name,
                         module_class_code, academic_year_start, semester_number, credits, registered_at, dropped_at,
                         status, component_score_1, component_score_2, process_score, final_exam_score,
                         course_score_grade_10, course_score_grade_letter, course_score_grade_4,
                         grade_published_at, grade_lock_at, note, created_at, updated_at, created_by, updated_by)
SELECT attempt.student_id, attempt.module_class_id, attempt.subject_id, attempt.semester_id,
       attempt.subject_code, attempt.subject_name, attempt.module_class_code,
       attempt.academic_year, attempt.semester_number, attempt.snapshot_credits,
       attempt.registration_start + INTERVAL '1 day 9 hours',
       CASE WHEN attempt.is_dropped THEN attempt.registration_start + INTERVAL '2 days 10 hours' END,
       CASE WHEN attempt.is_dropped THEN 'DROPPED' WHEN attempt.course_score < 4 THEN 'FAILED'
            WHEN attempt.course_score IS NOT NULL THEN 'COMPLETED' ELSE 'REGISTERED' END,
       CASE WHEN attempt.is_dropped THEN NULL ELSE attempt.component_score_1 END,
       CASE WHEN attempt.is_dropped THEN NULL ELSE attempt.component_score_2 END,
       attempt.process_score, attempt.final_exam_score, attempt.course_score, grade_band.letter, grade_band.grade_point,
       CASE WHEN attempt.course_score IS NOT NULL THEN attempt.last_teaching_date + INTERVAL '3 days 9 hours' END,
       CASE WHEN attempt.course_score IS NOT NULL THEN attempt.last_teaching_date + INTERVAL '17 days 9 hours' END,
       CASE WHEN attempt.is_dropped THEN 'Hủy trong thời gian đăng ký; giữ lịch sử để đăng ký lại.'
            WHEN attempt.is_retake THEN 'Lượt học lại/học bù; kết quả lượt trước được giữ nguyên.'
            WHEN attempt.academic_year = 2026 AND attempt.course_score IS NULL THEN 'Chờ công bố điểm học phần.'
            WHEN attempt.academic_year = 2025 AND attempt.semester_number = 2
                AND attempt.subject_code = 'MATH101' AND attempt.student_sequence % 11 = 0
                THEN 'Chờ hoàn tất điểm và thi bổ sung.'
            WHEN attempt.component_score_1 = 0 THEN 'Điểm TP1 bằng 0 đã được nhập, không phải ô trống.'
            WHEN attempt.component_score_2 IS NULL THEN 'Chờ nhập TP2.'
            ELSE '' END,
       attempt.registration_start + INTERVAL '1 day 2 hours',
       CASE WHEN attempt.course_score IS NOT NULL THEN attempt.last_teaching_date + INTERVAL '3 days 2 hours'
            WHEN attempt.is_dropped THEN attempt.registration_start + INTERVAL '2 days 3 hours'
            ELSE attempt.start_date::TIMESTAMP + INTERVAL '21 days' END,
       'demo-seed', 'demo-seed'
FROM course_scores attempt
LEFT JOIN LATERAL (
    SELECT letter, grade_point FROM (VALUES
        (0.0, 'F', 0.0), (4.0, 'D', 1.0), (5.0, 'D+', 1.5), (5.5, 'C', 2.0),
        (6.5, 'C+', 2.5), (7.0, 'B', 3.0), (8.0, 'B+', 3.5), (8.5, 'A', 4.0), (9.5, 'A+', 4.0)
    ) AS demo_grade_scale(minimum_score, letter, grade_point)
    WHERE minimum_score <= attempt.course_score ORDER BY minimum_score DESC LIMIT 1
) grade_band ON TRUE
ORDER BY attempt.student_id, attempt.academic_year, attempt.semester_number, attempt.subject_code;

-- Tập gọn phục vụ cả sĩ số và đối chiếu lịch; giữ mọi lượt còn hiệu lực, kể cả kết quả đã công bố.
CREATE TEMP TABLE demo_active_class_enrollments ON COMMIT DROP AS
SELECT module_class_id, student_id FROM enrollments WHERE status <> 'DROPPED' AND NOT is_deleted;
CREATE UNIQUE INDEX ON demo_active_class_enrollments (module_class_id, student_id);
ANALYZE demo_active_class_enrollments;

-- Lớp mới đều có sĩ số 0; chỉ cập nhật những lớp có người học và tổng hợp một lần cho mỗi lớp.
UPDATE module_classes module_class SET current_size = class_sizes.active_student_count
FROM (
    SELECT module_class_id, count(*)::INTEGER AS active_student_count
    FROM demo_active_class_enrollments GROUP BY module_class_id
) class_sizes WHERE class_sizes.module_class_id = module_class.id;

-- Buổi seed luôn là ngày thực tế và một ca cố định. Gom cặp lớp giao ca trước khi đối chiếu sinh viên chung;
-- tránh nhân từng lượt học với mọi buổi rồi GROUP BY một tập trung gian lớn.
-- Buổi trùng trong cùng một lớp đã bị chặn bởi kiểm tra phòng/giảng viên ở phía trên.
CREATE TEMP TABLE demo_overlapping_module_classes ON COMMIT DROP AS
SELECT DISTINCT first_schedule.module_class_id AS first_module_class_id,
                second_schedule.module_class_id AS second_module_class_id
FROM class_schedules first_schedule JOIN class_schedules second_schedule
    ON first_schedule.module_class_id < second_schedule.module_class_id
    AND first_schedule.start_date = second_schedule.start_date
    AND first_schedule.lesson_order_start = second_schedule.lesson_order_start
WHERE NOT first_schedule.is_deleted AND NOT second_schedule.is_deleted;
CREATE UNIQUE INDEX ON demo_overlapping_module_classes (first_module_class_id, second_module_class_id);
ANALYZE demo_overlapping_module_classes;

-- Ngày đạt đầu tiên đủ để kiểm tra tiên quyết tại thời điểm đăng ký; không tính lại theo từng lượt học.
CREATE TEMP TABLE demo_subject_pass_dates ON COMMIT DROP AS
SELECT student_id, subject_id, min(grade_published_at) AS first_passed_at
FROM enrollments WHERE status = 'COMPLETED' GROUP BY student_id, subject_id;
CREATE UNIQUE INDEX ON demo_subject_pass_dates (student_id, subject_id);
ANALYZE demo_subject_pass_dates;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM enrollments WHERE registered_at >= TIMESTAMP '2026-11-29 00:00:00'
               OR dropped_at >= TIMESTAMP '2026-11-29 00:00:00' OR grade_published_at >= TIMESTAMP '2026-11-29 00:00:00')
    THEN RAISE EXCEPTION 'Bộ seed có sự kiện học tập xảy ra sau mốc demo 28/11/2026'; END IF;
    IF EXISTS (SELECT 1 FROM enrollments WHERE academic_year_start > 2025
               AND (student_id <> (SELECT student_id FROM demo_what_if_student)
                    OR academic_year_start <> 2026 OR semester_number <> 1))
    THEN RAISE EXCEPTION 'Kỳ học mới chỉ có chín lượt demo của student, kỳ 2 năm học 2026–2027 phải để trống'; END IF;
    IF EXISTS (SELECT 1 FROM enrollments enrollment JOIN students student ON student.id = enrollment.student_id
               WHERE enrollment.registered_at < student.created_at + INTERVAL '7 hours')
    THEN RAISE EXCEPTION 'Lượt đăng ký không được có trước hồ sơ sinh viên'; END IF;
    IF EXISTS (
        SELECT 1 FROM demo_overlapping_module_classes overlapping_classes
        JOIN demo_active_class_enrollments first_enrollment
            ON first_enrollment.module_class_id = overlapping_classes.first_module_class_id
        JOIN demo_active_class_enrollments second_enrollment
            ON second_enrollment.module_class_id = overlapping_classes.second_module_class_id
            AND second_enrollment.student_id = first_enrollment.student_id
    ) THEN RAISE EXCEPTION 'Bộ seed có thời khóa biểu sinh viên trùng ca'; END IF;
    IF EXISTS (
        SELECT 1 FROM enrollments enrollment JOIN subject_prerequisites prerequisite ON prerequisite.subject_id = enrollment.subject_id
        LEFT JOIN demo_subject_pass_dates prerequisite_pass
            ON prerequisite_pass.student_id = enrollment.student_id
            AND prerequisite_pass.subject_id = prerequisite.prerequisite_subject_id
        WHERE NOT prerequisite.is_deleted
            AND (prerequisite_pass.first_passed_at IS NULL OR prerequisite_pass.first_passed_at >= enrollment.registered_at)
    ) THEN RAISE EXCEPTION 'Bộ seed có lượt học chưa đạt tiên quyết trước khi đăng ký'; END IF;
    IF EXISTS (SELECT 1 FROM module_classes module_class JOIN subjects subject ON subject.id = module_class.subject_id
               LEFT JOIN demo_class_schedule_summaries schedule_summary ON schedule_summary.module_class_id = module_class.id
               WHERE module_class.status IN ('OPEN','SCHEDULED','CLOSED','COMPLETED')
                 AND coalesce(schedule_summary.session_count, 0) <> (subject.total_teaching_periods + 2) / 3)
    THEN RAISE EXCEPTION 'Bộ seed có lớp đã xếp nhưng thiếu hoặc thừa số ca'; END IF;
    IF (SELECT count(*) FROM enrollments WHERE student_id = (SELECT student_id FROM demo_what_if_student)
            AND academic_year_start = 2026 AND semester_number = 1) <> 9
       OR (SELECT count(*) FROM enrollments WHERE student_id = (SELECT student_id FROM demo_what_if_student)
            AND academic_year_start = 2026 AND semester_number = 1 AND status = 'COMPLETED'
            AND course_score_grade_4 IS NOT NULL AND grade_published_at IS NOT NULL) <> 4
       OR (SELECT count(*) FROM enrollments WHERE student_id = (SELECT student_id FROM demo_what_if_student)
            AND academic_year_start = 2026 AND semester_number = 1 AND status = 'REGISTERED'
            AND course_score_grade_4 IS NULL AND grade_published_at IS NULL AND grade_lock_at IS NULL) <> 5
    THEN RAISE EXCEPTION 'What-If phải hiển thị bốn môn đã công bố và năm môn có thể thử điểm'; END IF;
    IF (SELECT sum(credits) FROM enrollments WHERE student_id = (SELECT student_id FROM demo_what_if_student)
            AND academic_year_start = 2026 AND semester_number = 1)
       > (SELECT config_value::INTEGER FROM system_configs WHERE config_key = 'enrollment.registration.maximum-credits')
    THEN RAISE EXCEPTION 'Tổng tín chỉ kỳ What-If vượt trần đăng ký đã cấu hình cho bộ demo'; END IF;
END $$;
