package com.eduhub.edu_hub.scheduling.api.mapper;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.scheduling.TimetableTestData;
import com.eduhub.edu_hub.scheduling.api.dto.response.TimetableAllocationResponse;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableAllocation;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class TimetablePlanResponseMapperTest {
    @ParameterizedTest
    @CsvSource({"1,1,3,07:00,09:25", "2,4,6,09:30,11:55", "3,7,9,12:35,15:00", "4,10,12,15:05,17:30"})
    void exposesConfirmedShiftTimesAsHourAndMinute(
            int shift, int lessonOrderStart, int lessonOrderEnd, String startTime, String endTime) {
        var response = mapSession(shift);
        assertThat(response.shift()).isEqualTo(shift);
        assertThat(response.lessonOrderStart()).isEqualTo(lessonOrderStart);
        assertThat(response.lessonOrderEnd()).isEqualTo(lessonOrderEnd);
        assertThat(response.startTime()).isEqualTo(LocalTime.parse(startTime));
        assertThat(response.endTime()).isEqualTo(LocalTime.parse(endTime));
        var json = JsonMapper.builder().build().writeValueAsString(response);
        assertThat(json).contains("\"startTime\":\"" + startTime + "\"", "\"endTime\":\"" + endTime + "\"");
    }

    @Test
    void doesNotInventDisplayTimesForUnassignedDiagnosticSession() {
        var response = mapSession(0);
        assertThat(response.shift()).isZero();
        assertThat(response.startTime()).isNull();
        assertThat(response.endTime()).isNull();
    }

    private TimetableAllocationResponse.SessionResponse mapSession(int shift) {
        var problem = TimetableTestData.problem(1, 1, 6);
        var originalAllocation = TimetableTestData.allocation(problem);
        var originalSession = originalAllocation.sessions().getFirst();
        var session = new TimetableAllocation.SessionPlacement(
                originalSession.moduleClassId(),
                originalSession.sessionIndex(),
                originalSession.lecturerId(),
                originalSession.roomId(),
                originalSession.date(),
                shift);
        var allocation = new TimetableAllocation(
                originalAllocation.phaseOneStart(),
                originalAllocation.phaseOneEnd(),
                originalAllocation.phaseTwoStart(),
                originalAllocation.subjects(),
                List.of(session));
        return TimetablePlanResponseMapper.toAllocationResponse(problem, allocation)
                .sessions()
                .getFirst();
    }
}
