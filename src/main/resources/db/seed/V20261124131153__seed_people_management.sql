-- Bộ dữ liệu giả lập cho database local được tạo mới; mốc nghiệp vụ cố định 2026-11-24.
-- Giữ năm khóa gần nhất K6–K10 (2022–2026); số khóa và mã đã dùng không đánh lại từ đầu.
-- Chặn trước file đầu tiên để không ghi đè cấu hình hoặc trộn bộ demo vào database đang dùng.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM users) OR EXISTS (SELECT 1 FROM faculties) OR EXISTS (SELECT 1 FROM cohorts)
       OR EXISTS (SELECT 1 FROM subjects) OR EXISTS (SELECT 1 FROM majors) OR EXISTS (SELECT 1 FROM semesters)
       OR EXISTS (SELECT 1 FROM rooms) OR EXISTS (SELECT 1 FROM knowledge_sources) OR EXISTS (SELECT 1 FROM audit_logs)
       OR EXISTS (SELECT 1 FROM system_configs WHERE config_value IS NOT NULL)
    THEN RAISE EXCEPTION 'Bộ seed demo yêu cầu database local mới/rỗng; không nạp vào dữ liệu đang sử dụng'; END IF;
END $$;

INSERT INTO faculties (faculty_code, faculty_name, active, created_at, updated_at, created_by, updated_by)
SELECT faculty_code, faculty_name, TRUE, opened_on::TIMESTAMP, opened_on::TIMESTAMP, 'demo-seed', 'demo-seed'
FROM (VALUES
    ('CNTT', 'Công nghệ thông tin', DATE '2016-01-04'),
    ('ATTT', 'An toàn thông tin', DATE '2016-01-04'),
    ('DTVT', 'Điện tử viễn thông', DATE '2016-01-04'),
    ('TTNT', 'Trí tuệ nhân tạo', DATE '2021-01-04'),
    ('KHCB', 'Khoa học cơ bản', DATE '2016-01-04')
) AS demo_faculties(faculty_code, faculty_name, opened_on);

INSERT INTO cohorts (cohort_code, code_sequence, start_year, end_year, created_at, updated_at, created_by, updated_by)
SELECT 'K' || (admission_year - 2016), admission_year - 2016, admission_year, admission_year + 4,
       make_date(admission_year, 3, 1)::TIMESTAMP, make_date(admission_year, 3, 1)::TIMESTAMP,
       'demo-seed', 'demo-seed'
FROM generate_series(2022, 2026) AS admission_year;

-- Các giá trị sau chỉ là quy ước demo, không thay quy chế hoặc cấu hình của profile khác.
-- Tắt gửi hàng đợi để có thể xem PENDING/retry mà không tự phát thư đến địa chỉ giả lập.
UPDATE system_configs configuration
SET config_value = demo_configurations.config_value, revision = configuration.revision + 1,
    updated_at = TIMESTAMP '2026-11-23 01:00:00', updated_by = 'demo-seed'
FROM (VALUES
    ('people.major-code-prefixes', '{"CNTT":"CT","ATTT":"AT","DTVT":"DT","TTNT":"AI"}'),
    ('people.faculty-code-prefixes', '{"CNTT":"CT","ATTT":"AT","DTVT":"DT","TTNT":"AI","KHCB":"CB"}'),
    ('curriculum.training-type-code-prefixes', '{"CHINH_QUY":"CQ","LIEN_THONG":"LT","VAN_BANG_2":"VB2"}'),
    ('enrollment.registration.minimum-credits', '12'),
    ('enrollment.registration.maximum-credits', '24'),
    ('notification.admission-enabled', 'false')
) AS demo_configurations(config_key, config_value)
WHERE configuration.config_key = demo_configurations.config_key;
