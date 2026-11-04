package com.eduhub.edu_hub.people.application;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

@Component
@RequiredArgsConstructor
public class PostgresLecturerCodeGenerator implements LecturerCodeGenerator {
    private final JdbcTemplate jdbc;

    public String generate() {
        Long number = jdbc.queryForObject("select nextval('lecturer_code_seq')", Long.class);
        if (number == null || number < 1 || number > 999999) {
            throw new BaseException(ErrorCode.LECTURER_CODE_EXHAUSTED);
        }
        return String.format(Locale.ROOT, "GV%06d", number);
    }
}
