-- Sĩ số mỗi ngành/khóa dao động 80–100, chia đều thành hai lớp 40–50 người.
-- last_student_sequence chốt số đã cấp; seed tài khoản kế tiếp tạo đủ hồ sơ tương ứng.
WITH admission_targets AS (
    SELECT major.id AS major_id, major.major_code, cohort.id AS cohort_id, cohort.code_sequence, cohort.start_year,
           CASE major.major_code
               WHEN 'CNTT' THEN 80 + ((cohort.code_sequence - 5) % 3) * 10
               WHEN 'ATTT' THEN 90 + ((cohort.code_sequence - 6) % 2) * 10
               WHEN 'DTVT' THEN 100 - ((cohort.code_sequence - 6) % 3) * 10
               ELSE 80 + ((cohort.code_sequence - 5) % 3) * 10 END AS student_count,
           CASE major.major_code WHEN 'CNTT' THEN 'CT' WHEN 'ATTT' THEN 'AT' WHEN 'DTVT' THEN 'DT' ELSE 'AI' END AS code_prefix
    FROM majors major CROSS JOIN cohorts cohort
    WHERE major.major_code <> 'TTNT' OR cohort.start_year >= 2021
), class_targets AS (
    SELECT admission_targets.*, (student_count + 49) / 50 AS class_count FROM admission_targets
)
INSERT INTO administrative_classes (class_name, major_id, cohort_id, training_type, min_capacity, max_capacity,
                                     reserve_capacity, last_student_sequence, created_at, updated_at, created_by, updated_by)
SELECT code_prefix || code_sequence || chr(64 + class_position), major_id, cohort_id, 'CHINH_QUY', 40, 50, 15,
       student_count / class_count + CASE WHEN class_position <= student_count % class_count THEN 1 ELSE 0 END,
       make_date(start_year, 7, 15)::TIMESTAMP, make_date(start_year, 7, 15)::TIMESTAMP, 'demo-seed', 'demo-seed'
FROM class_targets CROSS JOIN LATERAL generate_series(1, class_count) AS class_position
ORDER BY start_year, major_code, class_position;
