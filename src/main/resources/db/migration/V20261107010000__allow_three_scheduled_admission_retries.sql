-- One first delivery + three retries = four possible failed deliveries.
-- Existing notifications with three failures retain their count and get their final retry.
ALTER TABLE admission_notifications
    DROP CONSTRAINT admission_notifications_failure_count_check;
ALTER TABLE admission_notifications
    ADD CONSTRAINT chk_admission_notification_failure_count CHECK (failure_count BETWEEN 0 AND 4);

-- class_name is deliberately unchanged: historical L01/L02 classes and their MSSV are preserved.
-- TODO(CLASS_PREFIX_CONFIG): introduce prefix configuration and explicit, reviewed rename handling later.
-- Do not add global uniqueness on class_name: different majors may share a derived prefix.
