ALTER TABLE administrative_classes
    ADD CONSTRAINT chk_administrative_class_student_code_capacity
        CHECK (max_capacity <= 99);
