-- Lịch sử nhập học được nối tới đúng User/Student/lớp/khóa đã tạo; mọi nội dung đều là dữ liệu demo.
INSERT INTO admission_plans (major_id, cohort_id, total_students, min_capacity, max_capacity, reserve_capacity,
                             selected_class_count, state, revision, created_at, updated_at, created_by, updated_by)
SELECT administrative_class.major_id, cohort.id, count(student.id)::INTEGER, 40, 50, 15,
       count(DISTINCT administrative_class.id)::INTEGER,
       CASE WHEN major.major_code = 'ATTT' AND cohort.start_year = 2026 THEN 'PARTIALLY_CONFIRMED' ELSE 'CONFIRMED' END,
       1, make_date(cohort.start_year, 7, 10) + TIME '01:00', make_date(cohort.start_year, 7, 16) + TIME '02:00',
       'demo-seed', 'demo-seed'
FROM administrative_classes administrative_class JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id
JOIN majors major ON major.id = administrative_class.major_id JOIN students student ON student.class_id = administrative_class.id
GROUP BY administrative_class.major_id, cohort.id, major.major_code, cohort.start_year;

INSERT INTO admission_plan_classes (plan_id, class_id, class_position, target_size,
                                    created_at, updated_at, created_by, updated_by)
SELECT plan.id, administrative_class.id,
       row_number() OVER (PARTITION BY plan.id ORDER BY administrative_class.class_name)::INTEGER,
       (SELECT count(*) FROM students student WHERE student.class_id = administrative_class.id),
       make_date(cohort.start_year, 7, 15) + TIME '01:00', make_date(cohort.start_year, 7, 16) + TIME '02:00',
       'demo-seed', 'demo-seed'
FROM admission_plans plan JOIN cohorts cohort ON cohort.id = plan.cohort_id
JOIN administrative_classes administrative_class ON administrative_class.major_id = plan.major_id AND administrative_class.cohort_id = plan.cohort_id;

INSERT INTO admission_applicants (plan_id, row_number, application_code, citizen_id, full_name, date_of_birth,
                                  gender, email, phone, entrance_score, state, reuse_account, class_id, student_id,
                                  created_at, updated_at, created_by, updated_by)
SELECT plan.id, 7 + row_number() OVER (PARTITION BY plan.id ORDER BY student.student_code)::INTEGER,
       student.admission_application_code, student.citizen_id, student.full_name, student.date_of_birth,
       student.gender, account.email, student.phone, student.entrance_score, 'ENROLLED', FALSE, student.class_id, student.id,
       make_date(cohort.start_year, 7, 10) + TIME '01:00', make_date(cohort.start_year, 7, 16) + TIME '02:00',
       'demo-seed', 'demo-seed'
FROM students student JOIN users account ON account.id = student.user_id
JOIN administrative_classes administrative_class ON administrative_class.id = student.class_id
JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id
JOIN admission_plans plan ON plan.major_id = administrative_class.major_id AND plan.cohort_id = administrative_class.cohort_id;

INSERT INTO student_admissions (application_code, student_id, plan_id, class_id, citizen_id, full_name, date_of_birth,
                                gender, email, phone, entrance_score, activity_status,
                                created_at, updated_at, created_by, updated_by)
SELECT applicant.application_code, applicant.student_id, applicant.plan_id, applicant.class_id,
       applicant.citizen_id, applicant.full_name, applicant.date_of_birth, applicant.gender, applicant.email,
       applicant.phone, applicant.entrance_score, 'STUDYING', applicant.updated_at, applicant.updated_at, 'demo-seed', 'demo-seed'
FROM admission_applicants applicant WHERE applicant.state = 'ENROLLED';

-- Thông báo lịch sử đã kết thúc; chỉ tài khoản first-login K10 có mail còn chờ hoặc lỗi retry.
-- notification.admission-enabled=false giữ trạng thái demo ổn định khi ứng dụng khởi động.
INSERT INTO admission_notifications (admission_id, student_id, recipient, kind, state, failure_count,
                                     next_attempt_at, last_error, created_at, updated_at, created_by, updated_by)
SELECT admission.id, student.id, admission.email, 'INITIAL_ACCOUNT',
       CASE WHEN admission.email IS NULL OR student.is_deleted THEN 'SKIPPED'
            WHEN account.is_first_login AND student.id % 3 = 0 THEN 'PENDING'
            WHEN account.is_first_login THEN 'FAILED' ELSE 'SENT' END,
       CASE WHEN admission.email IS NULL OR student.is_deleted THEN 0
            WHEN account.is_first_login AND student.id % 3 = 1 THEN 1
            WHEN account.is_first_login AND student.id % 3 = 2 THEN 4
            WHEN NOT account.is_first_login THEN student.id % 3 ELSE 0 END,
       CASE WHEN account.is_first_login THEN TIMESTAMP '2026-11-23 09:00:00'
            ELSE (admission.created_at AT TIME ZONE 'UTC') AT TIME ZONE 'Asia/Ho_Chi_Minh' END,
       CASE WHEN admission.email IS NULL THEN 'NO_EMAIL' WHEN student.is_deleted THEN 'ACCOUNT_NOT_AVAILABLE'
            WHEN account.is_first_login AND student.id % 3 <> 0 THEN 'EMAIL_SEND_FAILED' ELSE NULL END,
       admission.created_at,
       CASE WHEN account.is_first_login THEN TIMESTAMP '2026-11-23 02:00:00' ELSE admission.created_at END,
       'demo-seed', 'demo-seed'
FROM student_admissions admission JOIN students student ON student.id = admission.student_id
JOIN users account ON account.id = student.user_id;

-- Phương án ATTT K10 có hồ sơ chờ, lỗi parse, xung đột, hồ sơ trùng và một dòng đã bỏ.
CREATE TEMP TABLE demo_pending_admission_scope ON COMMIT DROP AS
SELECT plan.id AS plan_id, plan_class.class_id, plan.total_students + 7 AS last_row_number
FROM admission_plans plan JOIN majors major ON major.id = plan.major_id JOIN cohorts cohort ON cohort.id = plan.cohort_id
JOIN admission_plan_classes plan_class ON plan_class.plan_id = plan.id AND plan_class.class_position = 1
WHERE major.major_code = 'ATTT' AND cohort.start_year = 2026;

INSERT INTO admission_applicants (plan_id, row_number, application_code, citizen_id, full_name, date_of_birth,
                                  gender, email, phone, entrance_score, state, error_message, warning_message,
                                  reuse_account, class_id, student_id, is_deleted,
                                  created_at, updated_at, created_by, updated_by)
SELECT scope.plan_id, scope.last_row_number + pending_position,
       CASE WHEN pending_position IN (11,12,13) THEN existing_student.admission_application_code
            ELSE 'HS-2026-ATTT-BS-' || lpad(pending_position::TEXT, 3, '0') END,
       CASE WHEN pending_position = 8 THEN reused_student.citizen_id
            WHEN pending_position IN (9,14) THEN '12345'
            WHEN pending_position IN (11,12,13) THEN existing_student.citizen_id
            ELSE '997208' || lpad(pending_position::TEXT, 6, '0') END,
       CASE WHEN pending_position = 8 THEN reused_student.full_name
            WHEN pending_position IN (11,12,13) THEN existing_student.full_name
            ELSE (ARRAY['Nguyễn Minh Khôi','Trần Ngọc Ánh','Lê Đức Khang','Phạm Thu Hà','Hoàng Gia Bảo',
                        'Vũ Phương Linh','Đặng Quang Huy','Hồ Minh An','Bùi Thanh Mai','Đỗ Trọng Nam',
                        'Ngô Thu Trang','Dương Hải Sơn','Lý Ngọc Diệp','Đinh Minh Châu'])[pending_position] END,
       CASE WHEN pending_position = 8 THEN reused_student.date_of_birth
            WHEN pending_position IN (11,12,13) THEN existing_student.date_of_birth ELSE DATE '2008-04-12' END,
       CASE WHEN pending_position = 8 THEN reused_student.gender
            WHEN pending_position IN (11,12,13) THEN existing_student.gender ELSE 'NAM' END,
       CASE WHEN pending_position = 8 THEN reused_account.email
            WHEN pending_position IN (11,12,13) THEN existing_account.email
            ELSE 'applicant.' || pending_position || '@admission.example.test' END,
       CASE WHEN pending_position = 8 THEN reused_student.phone
            WHEN pending_position IN (11,12,13) THEN existing_student.phone ELSE '0900000' || lpad(pending_position::TEXT, 3, '0') END,
       CASE WHEN pending_position = 10 THEN NULL WHEN pending_position IN (11,12,13) THEN existing_student.entrance_score ELSE 25.75 END,
       CASE WHEN pending_position = 7 THEN 'FAILED' WHEN pending_position IN (9,10,14) THEN 'INVALID'
            WHEN pending_position IN (11,12) THEN 'CONFLICT' WHEN pending_position = 13 THEN 'EXISTING' ELSE 'VALID' END,
       CASE WHEN pending_position = 7 THEN 'Lỗi lưu hồ sơ giả lập; có thể thử xác nhận lại.'
            WHEN pending_position IN (9,14) THEN 'CCCD phải có đúng 12 chữ số.'
            WHEN pending_position = 10 THEN 'Chưa nhập điểm đầu vào.'
            WHEN pending_position IN (11,12) THEN 'Mã hồ sơ trùng nhưng dữ liệu đối chiếu khác nhau.' ELSE NULL END,
       CASE WHEN pending_position = 8 THEN 'Dùng lại Student/MSSV/User của lần nhập học cũ; không cấp lại mật khẩu.' END,
       pending_position = 8, CASE WHEN pending_position <= 8 THEN scope.class_id END,
       CASE WHEN pending_position = 8 THEN reused_student.id WHEN pending_position = 13 THEN existing_student.id END,
       pending_position = 14, TIMESTAMP '2026-11-23 02:00:00', TIMESTAMP '2026-11-23 02:00:00', 'demo-seed', 'demo-seed'
FROM demo_pending_admission_scope scope CROSS JOIN generate_series(1,14) AS pending_position
CROSS JOIN LATERAL (SELECT student.* FROM students student JOIN administrative_classes administrative_class ON administrative_class.id = student.class_id
                    JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id
                    WHERE cohort.start_year = 2022 AND NOT student.is_deleted ORDER BY student.id LIMIT 1) reused_student
JOIN users reused_account ON reused_account.id = reused_student.user_id
CROSS JOIN LATERAL (SELECT student.* FROM students student WHERE student.class_id = scope.class_id AND NOT student.is_deleted ORDER BY student.id LIMIT 1) existing_student
JOIN users existing_account ON existing_account.id = existing_student.user_id;

-- Tạo xung đột có thật giữa hai dòng; dòng EXISTING vẫn giữ nguyên snapshot của hồ sơ đã nhập học.
UPDATE admission_applicants applicant SET full_name = applicant.full_name || ' (khác thông tin)'
FROM demo_pending_admission_scope scope
WHERE applicant.plan_id = scope.plan_id AND applicant.row_number IN (scope.last_row_number + 11, scope.last_row_number + 12);

UPDATE admission_plans plan
SET total_students = (SELECT count(*) FROM admission_applicants applicant WHERE applicant.plan_id = plan.id
                      AND NOT applicant.is_deleted AND applicant.state IN ('VALID','FAILED','ENROLLED')),
    updated_at = TIMESTAMP '2026-11-23 02:00:00'
WHERE plan.id IN (SELECT plan_id FROM demo_pending_admission_scope);
UPDATE admission_plan_classes plan_class
SET target_size = (SELECT count(*) FROM admission_applicants applicant WHERE applicant.plan_id = plan_class.plan_id
                   AND applicant.class_id = plan_class.class_id AND NOT applicant.is_deleted AND applicant.state IN ('VALID','FAILED','ENROLLED')),
    updated_at = TIMESTAMP '2026-11-23 02:00:00'
WHERE plan_class.plan_id IN (SELECT plan_id FROM demo_pending_admission_scope);

DO $$ BEGIN
    IF (SELECT count(*) FROM cohorts) <> 5 OR (SELECT count(*) FROM curriculums) <> 20
    THEN RAISE EXCEPTION 'Bộ seed phải có 5 khóa và 20 CTĐT'; END IF;
    -- Chốt quy mô để phát hiện việc tra mã thiếu hoặc JOIN làm mất/nhân bản dữ liệu mẫu.
    IF (SELECT count(*) FROM students) <> 1850 OR (SELECT count(*) FROM administrative_classes) <> 40
       OR (SELECT count(*) FROM lecturers) <> 120 OR (SELECT count(*) FROM subjects) <> 124
       OR (SELECT count(*) FROM module_classes) <> 1227 OR (SELECT count(*) FROM class_schedules) <> 18300
       OR (SELECT count(*) FROM enrollments) <> 45975
    THEN RAISE EXCEPTION 'Quy mô bộ seed không khớp các khóa/ngành và lộ trình đã khai báo'; END IF;
    IF EXISTS (SELECT 1 FROM students student JOIN administrative_classes administrative_class ON administrative_class.id = student.class_id
               GROUP BY administrative_class.major_id, administrative_class.cohort_id
               HAVING count(*) NOT BETWEEN 80 AND 100)
    THEN RAISE EXCEPTION 'Sĩ số ngành/khóa không nằm trong khoảng 80–100'; END IF;
    IF EXISTS (SELECT 1 FROM administrative_classes administrative_class JOIN majors major ON major.id = administrative_class.major_id
               JOIN cohorts cohort ON cohort.id = administrative_class.cohort_id WHERE major.major_code = 'TTNT' AND cohort.start_year < 2021)
    THEN RAISE EXCEPTION 'Ngành AI chỉ bắt đầu sau bốn khóa đầu'; END IF;
    IF EXISTS (SELECT 1 FROM administrative_classes administrative_class
               WHERE administrative_class.last_student_sequence <> (SELECT max(right(student.student_code, 2)::INTEGER)
                   FROM students student WHERE student.class_id = administrative_class.id))
    THEN RAISE EXCEPTION 'Bộ đếm MSSV không khớp hồ sơ đã cấp'; END IF;
    IF EXISTS (SELECT 1 FROM admission_plans plan
               WHERE plan.total_students <> (SELECT count(*) FROM admission_applicants applicant WHERE applicant.plan_id = plan.id
                   AND NOT applicant.is_deleted AND applicant.state IN ('VALID','FAILED','ENROLLED')))
       OR EXISTS (SELECT 1 FROM admission_plan_classes plan_class
                  WHERE plan_class.target_size <> (SELECT count(*) FROM admission_applicants applicant
                      WHERE applicant.plan_id = plan_class.plan_id AND applicant.class_id = plan_class.class_id
                        AND NOT applicant.is_deleted AND applicant.state IN ('VALID','FAILED','ENROLLED')))
    THEN RAISE EXCEPTION 'Sĩ số phương án nhập học hoặc chỉ tiêu lớp không khớp hồ sơ'; END IF;
    IF (SELECT count(*) FROM admission_notifications) <> (SELECT count(*) FROM student_admissions)
    THEN RAISE EXCEPTION 'Mỗi lượt nhập học phải có đúng một thông báo'; END IF;
    IF EXISTS (SELECT 1 FROM users account WHERE account.username IN ('student','lecturer','academic_admin')
               AND (account.status <> 'ACTIVE' OR account.is_first_login OR account.is_deleted))
       OR (SELECT count(*) FROM users WHERE username IN ('student','lecturer','academic_admin')) <> 3
    THEN RAISE EXCEPTION 'Ba tài khoản local phải đăng nhập trực tiếp được'; END IF;
    IF EXISTS (SELECT 1 FROM students student LEFT JOIN people_user_links profile_link ON profile_link.student_id = student.id
               WHERE profile_link.user_id IS DISTINCT FROM student.user_id)
       OR EXISTS (SELECT 1 FROM lecturers lecturer LEFT JOIN people_user_links profile_link ON profile_link.lecturer_id = lecturer.id
                  WHERE profile_link.user_id IS DISTINCT FROM lecturer.user_id)
    THEN RAISE EXCEPTION 'Liên kết User với hồ sơ People không toàn vẹn'; END IF;
END $$;
