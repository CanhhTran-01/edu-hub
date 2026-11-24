-- Khóa mới có một năm bắt đầu duy nhất; giữ cả lịch sử đã xóa mềm và năm chưa biết của dữ liệu cũ.
-- Dừng để đối soát khi dữ liệu đã trùng, không tự gộp/xóa hoặc đổi mã của khóa đang được tham chiếu.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM cohorts
        WHERE start_year IS NOT NULL
        GROUP BY start_year HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cần đối soát các khóa trùng năm bắt đầu trước khi bổ sung ràng buộc tạo khóa';
    END IF;
    IF EXISTS (
        SELECT 1 FROM cohorts
        GROUP BY lower(btrim(cohort_code)) HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cần đối soát các mã khóa trùng sau chuẩn hóa trước khi bổ sung ràng buộc tạo khóa';
    END IF;
END;
$$;

CREATE UNIQUE INDEX uk_cohorts_start_year ON cohorts(start_year) WHERE start_year IS NOT NULL;
CREATE UNIQUE INDEX uk_cohorts_code_normalized ON cohorts(lower(btrim(cohort_code)));
