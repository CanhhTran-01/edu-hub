-- Development fixtures for admission import: curriculum owns the canonical major.
-- No students or accounts are created here.
DO $$
BEGIN
    IF (SELECT count(*) FROM majors WHERE major_code = 'CNTT' AND is_deleted = FALSE) <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one non-deleted CNTT major before seeding CT8 classes';
    END IF;
    IF (SELECT count(*) FROM cohorts WHERE cohort_code = 'CT8' AND is_deleted = FALSE) > 1 THEN
        RAISE EXCEPTION 'Multiple active CT8 cohorts found; resolve duplicates before seeding classes';
    END IF;
END $$;

INSERT INTO cohorts (cohort_code, created_at, updated_at, created_by, updated_by)
SELECT 'CT8', CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
       'admission-seed', 'admission-seed'
WHERE NOT EXISTS (
    SELECT 1 FROM cohorts WHERE cohort_code = 'CT8' AND is_deleted = FALSE
);

INSERT INTO administrative_classes (
    class_name, major_id, major_name, major_code, cohort_id, training_type,
    min_capacity, max_capacity, created_at, updated_at, created_by, updated_by
)
SELECT seed.class_name, major.id, major.major_name, major.major_code, cohort.id, 'CHINH_QUY',
       2, 50, CURRENT_TIMESTAMP AT TIME ZONE 'UTC', CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
       'admission-seed', 'admission-seed'
FROM (VALUES ('L01'), ('L02')) AS seed(class_name)
CROSS JOIN majors major
CROSS JOIN cohorts cohort
WHERE major.major_code = 'CNTT' AND major.is_deleted = FALSE
  AND cohort.cohort_code = 'CT8' AND cohort.is_deleted = FALSE
  AND NOT EXISTS (
      SELECT 1 FROM administrative_classes existing
      WHERE existing.class_name = seed.class_name
        AND existing.major_id = major.id
        AND existing.cohort_id = cohort.id
        AND existing.training_type = 'CHINH_QUY'
        AND existing.is_deleted = FALSE
  );
