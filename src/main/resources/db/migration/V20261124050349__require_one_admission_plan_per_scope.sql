-- Mỗi ngành–khóa chỉ có một phương án chưa xóa; không tự gộp hoặc xóa dữ liệu lịch sử bị trùng.
-- Nếu dừng ở kiểm tra này, cần đối soát các plan và liên kết nhập học trước khi áp dụng lại.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM admission_plans
        WHERE is_deleted = FALSE
        GROUP BY major_id, cohort_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Admission plans contain duplicate major/cohort scopes; reconcile existing plans before migration';
    END IF;
END $$;

CREATE UNIQUE INDEX uk_admission_plans_active_scope
    ON admission_plans (major_id, cohort_id)
    WHERE is_deleted = FALSE;
