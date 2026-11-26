package com.eduhub.edu_hub.academic.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.api.dto.response.StudentAcademicOverviewResponse;
import com.eduhub.edu_hub.academic.application.service.StudentAcademicQueryService;
import com.eduhub.edu_hub.people.api.dto.response.StudentAcademicProfileResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

class AcademicAdvisorFacadeImplTest {
    @Test
    void profilePreservesMissingHistoryAndZeroTargetWithoutPassingStudentIdentityToModel() {
        var queries = mock(StudentAcademicQueryService.class);
        var clock = Clock.fixed(Instant.parse("2026-11-25T17:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        var principal = Jwt.withTokenValue("test")
                .header("alg", "HS256")
                .subject("student")
                .build();
        var student = new StudentAcademicProfileResponse(
                99L,
                "PRIVATE_CODE",
                "PRIVATE_NAME",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                null);
        var semester =
                SemesterResponse.builder().id(7L).semesterName("Kỳ hiện tại").build();
        when(queries.findMyAcademicOverview(principal))
                .thenReturn(new StudentAcademicOverviewResponse(
                        student,
                        semester,
                        List.of(semester),
                        null,
                        new BigDecimal("0.00"),
                        null,
                        120,
                        18,
                        6,
                        1,
                        false,
                        2026,
                        "Lịch sử chưa đầy đủ"));

        var profile = new AcademicAdvisorFacadeImpl(queries, clock).findMyAcademicProfile(principal);

        assertThat(profile.cumulativeGpa()).isNull();
        assertThat(profile.accumulatedCredits()).isNull();
        assertThat(profile.currentSemesterGpa()).isEqualByComparingTo("0");
        assertThat(profile.graduationTargetGpa()).isEqualByComparingTo("0");
        assertThat(profile.historyComplete()).isFalse();
        assertThat(profile.observedAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 0, 0));
        var json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(profile);
        assertThat(json).doesNotContain("PRIVATE_CODE", "PRIVATE_NAME", "studentId", "studentCode", "fullName");
        verify(queries).findMyAcademicOverview(principal);
    }
}
