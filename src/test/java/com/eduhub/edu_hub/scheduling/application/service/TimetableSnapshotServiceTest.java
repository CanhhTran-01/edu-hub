package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.people.api.dto.response.TimetableLecturerResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.application.exception.TimetableException;
import com.eduhub.edu_hub.scheduling.domain.enums.*;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.repository.*;
import com.eduhub.edu_hub.scheduling.infrastructure.config.TimetablingSettings;
import com.eduhub.edu_hub.scheduling.infrastructure.persistence.TimetablePlanCodec;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TimetableSnapshotServiceTest {
    private final CurriculumFacade.ClassOpening curriculum = mock(CurriculumFacade.ClassOpening.class);
    private final PeopleFacade.Scheduling people = mock(PeopleFacade.Scheduling.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final ClassScheduleRepository schedules = mock(ClassScheduleRepository.class);
    private final TimetablePlanRepository plans = mock(TimetablePlanRepository.class);
    private final TimetablePlanCodec codec =
            new TimetablePlanCodec(JsonMapper.builder().build());
    private final TimetableSnapshotService service =
            new TimetableSnapshotService(curriculum, people, rooms, schedules, plans, codec, new TimetablingSettings());
    private final Semester semester = Semester.builder()
            .startDate(LocalDate.of(2027, 8, 7))
            .endDate(LocalDate.of(2027, 12, 31))
            .build();

    @Test
    void reportsMissingOfficialTeachingPeriodsInsteadOfInferringFromCredits() {
        var moduleClass = moduleClass();
        when(curriculum.requireOpenableSubjects(Set.of(1L)))
                .thenReturn(List.of(
                        SubjectSimpleResponse.builder().id(1L).credits(3).build()));
        assertThatThrownBy(() -> service.loadProblem(semester, List.of(moduleClass), Set.of()))
                .isInstanceOfSatisfying(TimetableException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TIMETABLE_INVALID_INPUT);
                    assertThat(exception.getErrors())
                            .anyMatch(error -> error.getField().equals("subjects[1].totalTeachingPeriods"));
                });
        verifyNoInteractions(schedules, plans);
    }

    @Test
    void readsActualCandidatesAndRoomsWithoutStudentHistoryDependency() {
        var moduleClass = moduleClass();
        when(curriculum.requireOpenableSubjects(Set.of(1L)))
                .thenReturn(List.of(SubjectSimpleResponse.builder()
                        .id(1L)
                        .subjectCode("CS1")
                        .credits(3)
                        .totalTeachingPeriods(45)
                        .build()));
        when(people.findEligibleLecturers(Set.of(1L)))
                .thenReturn(List.of(new TimetableLecturerResponse(201L, "Giảng viên", Set.of(1L))));
        var room = Room.builder()
                .roomCode("101-TA1")
                .capacity(60)
                .status(RoomStatus.ACTIVE)
                .build();
        room.setId(501L);
        when(rooms.findAllByDeletedFalseAndStatusOrderByIdAsc(RoomStatus.ACTIVE))
                .thenReturn(List.of(room));
        var problem = service.loadProblem(semester, List.of(moduleClass), Set.of());
        assertThat(problem.classes()).singleElement().satisfies(requirement -> assertThat(
                        requirement.totalTeachingPeriods())
                .isEqualTo(45));
        assertThat(codec.fingerprint(codec.read(codec.write(problem), TimetableProblem.class)))
                .isEqualTo(codec.fingerprint(problem));
        assertThat(problem.lecturers().getFirst().lecturerId()).isEqualTo(201L);
    }

    private ModuleClass moduleClass() {
        semester.setId(7L);
        var subject = Subject.builder().credits(3).build();
        subject.setId(1L);
        var moduleClass = ModuleClass.builder()
                .semester(semester)
                .subject(subject)
                .moduleClassCode("MC_7_CS1_001")
                .maxCapacity(60)
                .status(ModuleClassStatus.UNSCHEDULED)
                .build();
        moduleClass.setId(100L);
        return moduleClass;
    }
}
