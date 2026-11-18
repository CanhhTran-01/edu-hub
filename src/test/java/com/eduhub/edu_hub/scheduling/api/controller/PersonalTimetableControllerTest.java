package com.eduhub.edu_hub.scheduling.api.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.scheduling.api.dto.response.*;
import com.eduhub.edu_hub.scheduling.api.dto.response.WeeklyTimetableResponse.WeeklySessionResponse;
import com.eduhub.edu_hub.scheduling.api.exception.TimetableExceptionHandler;
import com.eduhub.edu_hub.scheduling.application.service.PersonalTimetableQueryService;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PersonalTimetableControllerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T17:30:00Z"),
            new TimeConfig().applicationClock().getZone());
    private final ApiResponseFactory apiResponseFactory = new ApiResponseFactory(CLOCK);
    PersonalTimetableQueryService service = mock(PersonalTimetableQueryService.class);
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .claim("token_use", "ACCESS")
            .build();
    MockMvc mvc;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(principal));
        mvc = MockMvcBuilders.standaloneSetup(new PersonalTimetableController(apiResponseFactory, service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(
                        new TimetableExceptionHandler(apiResponseFactory),
                        new GlobalExceptionHandler(apiResponseFactory))
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bindsPersonalAndSemesterRoutesAndSerializesLocalLessonTime() throws Exception {
        var monday = LocalDate.of(2026, 11, 16);
        var session = new WeeklySessionResponse(
                1L,
                2L,
                "MC2",
                3L,
                "CS3",
                "Học phần",
                4L,
                "Giảng viên",
                monday,
                2,
                1,
                3,
                1,
                LocalTime.of(7, 0),
                LocalTime.of(9, 25),
                5L,
                "101-TA1",
                "TA1",
                "101");
        when(service.findPersonalTimetableForStudent(eq(principal), eq(2L), any()))
                .thenReturn(new WeeklyTimetableResponse(
                        SemesterResponse.builder().id(2L).build(),
                        monday,
                        monday.plusDays(6),
                        List.of(session),
                        List.of()));
        mvc.perform(get("/api/v1/students/me/semesters/2/timetable").param("weekStartDate", "2026-11-16"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.sessions[0].startTime").value("07:00"))
                .andExpect(jsonPath("$.data.sessions[0].date").value("2026-11-16"));
        mvc.perform(get("/api/v1/lecturers/me/semesters/2/timetable").param("weekStartDate", "2026-11-16"))
                .andExpect(status().isOk());
        verify(service)
                .findPersonalTimetableForLecturer(
                        eq(principal), eq(2L), argThat(request -> monday.equals(request.getWeekStartDate())));
        mvc.perform(get("/api/v1/semesters/2/weekly-timetable").param("weekStartDate", "2026-11-16"))
                .andExpect(status().isOk());
        verify(service).findSemesterTimetable(eq(principal), eq(2L), any());
    }

    @Test
    void rejectsMissingOrMalformedWeekBeforeQuerying() throws Exception {
        mvc.perform(get("/api/v1/students/me/semesters/2/timetable")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/students/me/semesters/2/timetable").param("weekStartDate", "bad"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
