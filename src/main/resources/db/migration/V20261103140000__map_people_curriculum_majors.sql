-- Chỉ là tham chiếu vô hướng (scalar): khi chạy, việc đọc dữ liệu đi qua CurriculumFacade.Majors.
-- Các tiền tố legacy (cũ) được khai báo tường minh là ánh xạ đã được phê duyệt.
-- ID số tạm thời không bao giờ được coi là bằng chứng.
DO $$
DECLARE unresolved TEXT;
BEGIN
    SELECT string_agg(format('classId=%s class=%s majorCode=%s', ac.id, ac.class_name, ac.major_code), '; ')
    INTO unresolved
    FROM administrative_classes ac
    LEFT JOIN majors m ON upper(m.major_code) = CASE upper(trim(ac.major_code))
        WHEN 'CT' THEN 'CNTT' WHEN 'AT' THEN 'ATTT'
        WHEN 'DT' THEN 'DTVT' WHEN 'AI' THEN 'TTNT'
        ELSE upper(trim(ac.major_code)) END AND m.is_deleted = FALSE
    WHERE m.id IS NULL AND ac.is_deleted = FALSE;
    IF unresolved IS NOT NULL THEN
        RAISE EXCEPTION 'Unmapped People majors. Correct major_code using verified source data before retrying: %', unresolved;
    END IF;
END $$;

UPDATE administrative_classes ac
SET major_id = m.id, major_code = m.major_code, major_name = m.major_name FROM majors m
WHERE upper(m.major_code) = CASE upper(trim(ac.major_code))
    WHEN 'CT' THEN 'CNTT' WHEN 'AT' THEN 'ATTT'
    WHEN 'DT' THEN 'DTVT' WHEN 'AI' THEN 'TTNT'
    ELSE upper(trim(ac.major_code)) END
    AND m.is_deleted = FALSE
    AND ac.is_deleted = FALSE;
