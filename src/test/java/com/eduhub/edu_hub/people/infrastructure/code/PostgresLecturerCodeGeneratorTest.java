package com.eduhub.edu_hub.people.infrastructure.code;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.people.application.code.PostgresLecturerCodeGenerator;
import com.eduhub.edu_hub.shared.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresLecturerCodeGeneratorTest {
    @Test
    void formatsDatabaseSequenceAndRejectsOverflow() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("select nextval('lecturer_code_seq')", Long.class))
                .thenReturn(1L, 2L, 999999L, 1000000L);
        var generator = new PostgresLecturerCodeGenerator(jdbc);
        assertThat(generator.generate()).isEqualTo("GV000001");
        assertThat(generator.generate()).isEqualTo("GV000002");
        assertThat(generator.generate()).isEqualTo("GV999999");
        assertThatThrownBy(generator::generate).isInstanceOf(BaseException.class);
    }
}
