package com.eduhub.edu_hub.people.infrastructure.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Truy cập bộ đếm kỹ thuật theo khoa/năm tuyển dụng trong transaction do use-case mở. */
@Repository
@RequiredArgsConstructor
public class LecturerCodeCounterRepository {
    private final JdbcTemplate jdbcTemplate;

    public Optional<Integer> allocateNextLecturerSequence(
            Long facultyId, Integer recruitmentYear, int maximumLecturerSequence) {
        // UPSERT cấp số nguyên tử kể cả lần đầu của khoa/năm; cùng transaction tạo hồ sơ/tài khoản.
        // Chỉ số đã commit mới được xem là đã cấp; xóa mềm hồ sơ không giải phóng bộ đếm.
        return jdbcTemplate
                .queryForList(
                        """
                        INSERT INTO lecturer_code_counters (faculty_id, recruitment_year, last_lecturer_sequence)
                        VALUES (?, ?, 1)
                        ON CONFLICT (faculty_id, recruitment_year)
                        DO UPDATE SET last_lecturer_sequence = lecturer_code_counters.last_lecturer_sequence + 1
                        WHERE lecturer_code_counters.last_lecturer_sequence < ?
                        RETURNING last_lecturer_sequence
                        """,
                        Integer.class,
                        facultyId,
                        recruitmentYear,
                        maximumLecturerSequence)
                .stream()
                .findFirst();
    }
}
