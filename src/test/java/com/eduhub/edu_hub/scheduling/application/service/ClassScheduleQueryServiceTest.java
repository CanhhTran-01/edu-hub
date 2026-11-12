package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.repository.ClassScheduleRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.databind.json.JsonMapper;

class ClassScheduleQueryServiceTest {
    @ParameterizedTest
    @CsvSource({"1,1,3,07:00,09:25", "2,4,6,09:30,11:55", "3,7,9,12:35,15:00", "4,10,12,15:05,17:30"})
    void returnsActualScheduleAndHistoricalRoomDetailsInOneBatch(
            int shift, int lessonOrderStart, int lessonOrderEnd, String startTime, String endTime) {
        var repository = mock(ClassScheduleRepository.class);
        var service = new ClassScheduleQueryService(repository);
        var room = Room.builder()
                .roomCode("101-TA1")
                .buildingCode("TA1")
                .roomNumber("101")
                .status(RoomStatus.INACTIVE)
                .build();
        room.setId(501L);
        room.setDeleted(true);
        var moduleClass = ModuleClass.builder().build();
        moduleClass.setId(100L);
        var date = LocalDate.of(2027, 8, 9);
        var schedule = ClassSchedule.builder()
                .moduleClass(moduleClass)
                .room(room)
                .dayOfWeek(2)
                .lessonOrderStart(lessonOrderStart)
                .lessonOrderEnd(lessonOrderEnd)
                .startDate(date)
                .endDate(date)
                .build();
        schedule.setId(900L);
        when(repository.findAll(ArgumentMatchers.<Specification<ClassSchedule>>any(), any(Sort.class)))
                .thenReturn(List.of(schedule));
        assertThat(service.findSchedulesByClassIds(List.of(100L, 101L)).get(100L))
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.getId()).isEqualTo(900L);
                    assertThat(response.getRoomId()).isEqualTo(501L);
                    assertThat(response.getRoomCode()).isEqualTo("101-TA1");
                    assertThat(response.getStartDate()).isEqualTo(date);
                    assertThat(response.getShift()).isEqualTo(shift);
                    assertThat(response.getStartTime()).isEqualTo(LocalTime.parse(startTime));
                    assertThat(response.getEndTime()).isEqualTo(LocalTime.parse(endTime));
                    var json = JsonMapper.builder().build().writeValueAsString(response);
                    assertThat(json).contains("\"startTime\":\"" + startTime + "\"", "\"endTime\":\"" + endTime + "\"");
                });
        verify(repository, times(1)).findAll(ArgumentMatchers.<Specification<ClassSchedule>>any(), any(Sort.class));
    }

    @Test
    void leavesDisplayTimesUnknownForLegacyLessonRange() {
        var repository = mock(ClassScheduleRepository.class);
        var room = Room.builder().roomCode("101-TA1").build();
        room.setId(501L);
        var moduleClass = ModuleClass.builder().build();
        moduleClass.setId(100L);
        var schedule = ClassSchedule.builder()
                .moduleClass(moduleClass)
                .room(room)
                .lessonOrderStart(2)
                .lessonOrderEnd(4)
                .build();
        when(repository.findAll(ArgumentMatchers.<Specification<ClassSchedule>>any(), any(Sort.class)))
                .thenReturn(List.of(schedule));
        var response = new ClassScheduleQueryService(repository)
                .findSchedulesByClassIds(List.of(100L))
                .get(100L)
                .getFirst();
        assertThat(response.getLessonOrderStart()).isEqualTo(2);
        assertThat(response.getLessonOrderEnd()).isEqualTo(4);
        assertThat(response.getShift()).isNull();
        assertThat(response.getStartTime()).isNull();
        assertThat(response.getEndTime()).isNull();
    }
}
