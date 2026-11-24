-- Danh mục cấu hình kỹ thuật bắt buộc; không điền mapping nghiệp vụ hoặc sửa mã đã cấp.
INSERT INTO system_configs (config_key, created_at, updated_at, created_by, updated_by)
SELECT config_key, CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
       'system-config-migration', 'system-config-migration'
FROM (VALUES
    ('people.major-code-prefixes'),
    ('people.faculty-code-prefixes'),
    ('curriculum.training-type-code-prefixes')
) AS code_mapping_keys(config_key);

-- STT chung cho học phần; không cấp lại số khi rollback hoặc xóa mềm.
CREATE SEQUENCE subject_code_seq AS BIGINT START WITH 1 NO CYCLE;
-- Nếu đã có mã HP từ nguồn dữ liệu hợp lệ, giữ nguyên mã và tiếp tục sau STT lớn nhất, kể cả lịch sử.
WITH issued_sequences AS (
    SELECT SUBSTRING(subject_code FROM 3)::NUMERIC AS code_sequence
    FROM subjects WHERE subject_code ~ '^HP[0-9]{1,19}$'
)
SELECT setval('subject_code_seq', GREATEST(COALESCE(MAX(code_sequence)::BIGINT, 0), 1),
              COALESCE(MAX(code_sequence), 0) > 0)
FROM issued_sequences WHERE code_sequence BETWEEN 1 AND 9223372036854775807;
