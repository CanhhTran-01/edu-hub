package com.eduhub.edu_hub.scheduling.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcademicCalendarFacadeImplTest {
    @Test
    void usesVietnameseDateAndPrefersOngoingStudyBeforeUpcomingRegistration() {
        var repository = mock(SemesterRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-11-16T18:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        var today = LocalDate.of(2026, 11, 17);
        var upcoming = Semester.builder()
                .startDate(today.plusMonths(1))
                .endDate(today.plusMonths(5))
                .build();
        upcoming.setId(2L);
        var ongoing = Semester.builder()
                .startDate(today.minusMonths(1))
                .endDate(today)
                .build();
        ongoing.setId(1L);
        when(repository.findCurrentSemesterCandidates(today)).thenReturn(List.of(upcoming, ongoing));
        var facade = new AcademicCalendarFacadeImpl(repository, clock);
        assertThat(facade.findCurrentSemester().orElseThrow().getId()).isEqualTo(1L);
        when(repository.findCurrentSemesterCandidates(today)).thenReturn(List.of(upcoming));
        assertThat(facade.findCurrentSemester().orElseThrow().getId()).isEqualTo(2L);
        when(repository.findCurrentSemesterCandidates(today)).thenReturn(List.of());
        assertThat(facade.findCurrentSemester()).isEmpty();
    }
}
