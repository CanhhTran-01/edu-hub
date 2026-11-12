package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.TimetableTestData;
import com.eduhub.edu_hub.scheduling.api.dto.request.*;
import com.eduhub.edu_hub.scheduling.application.exception.TimetableException;
import com.eduhub.edu_hub.scheduling.domain.enums.*;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.repository.*;
import com.eduhub.edu_hub.scheduling.infrastructure.config.TimetablingSettings;
import com.eduhub.edu_hub.scheduling.infrastructure.persistence.TimetablePlanCodec;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class TimetablePlanApplicationServiceTest {
    @Mock
    private SemesterRepository semesterRepository;

    @Mock
    private ModuleClassRepository moduleClassRepository;

    @Mock
    private ClassScheduleRepository scheduleRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private TimetablePlanRepository planRepository;

    @Mock
    private TimetableSnapshotService snapshotService;

    @Mock
    private TimetablePlanExecutionService executionService;

    @Mock
    private ModuleClassApplicationService moduleClassService;

    @Mock
    private PeopleFacade.Scheduling peopleFacade;

    @Mock
    private CurriculumFacade.ClassOpening curriculumFacade;

    @Mock
    private IdentityFacade identityFacade;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EntityManager entityManager;

    @Spy
    private TimetablingSettings settings = new TimetablingSettings();

    @Spy
    private TimetablePlanCodec codec =
            new TimetablePlanCodec(JsonMapper.builder().build());

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @InjectMocks
    private TimetablePlanApplicationService service;

    private final Jwt principal =
            Jwt.withTokenValue("token").header("alg", "HS256").subject("admin").build();
    private final TimetableProblem problem = TimetableTestData.problem(1, 1, 6);

    @Test
    void rejectsDirectUnauthenticatedCallsBeforeAccessingData() {
        assertError(() -> service.createPlan(null, 7L, new CreateTimetablePlanRequest()), ErrorCode.UNAUTHENTICATED);
        assertError(() -> service.findPlan(null, 9L), ErrorCode.UNAUTHENTICATED);
        assertError(() -> service.applyPlan(null, 9L, new ApplyTimetablePlanRequest(0L)), ErrorCode.UNAUTHENTICATED);
        verifyNoInteractions(semesterRepository, planRepository, snapshotService);
    }

    @Test
    void createsDurableDraftAndPublishesWorkOnlyAfterSaving() {
        var semester = semester();
        when(semesterRepository.findForUpdateByIdAndDeletedFalse(7L)).thenReturn(Optional.of(semester));
        when(moduleClassRepository.findAllBySemester_IdOrderByIdAsc(7L)).thenReturn(List.of(moduleClass(semester)));
        when(snapshotService.loadProblem(eq(semester), anyList(), eq(Set.of()))).thenReturn(problem);
        when(planRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TimetablePlan plan = invocation.getArgument(0);
            plan.setId(9L);
            return plan;
        });
        var response = service.createPlan(principal, 7L, new CreateTimetablePlanRequest());
        assertThat(response.state()).isEqualTo(TimetablePlanState.QUEUED);
        assertThat(response.canApply()).isFalse();
        var ordering = inOrder(planRepository, eventPublisher);
        ordering.verify(planRepository).existsByStateInAndDeletedFalse(any());
        ordering.verify(planRepository).saveAndFlush(any());
        ordering.verify(eventPublisher).publishEvent(new TimetablePlanApplicationService.PlanCreated(9L));
        verifyNoInteractions(scheduleRepository, entityManager);
    }

    @Test
    void blocksSolvingOnceRegistrationStartsEvenWithZeroStudents() {
        var semester = semester();
        semester.setRegistrationStart(LocalDate.now(clock));
        when(semesterRepository.findForUpdateByIdAndDeletedFalse(7L)).thenReturn(Optional.of(semester));
        when(moduleClassRepository.findAllBySemester_IdOrderByIdAsc(7L)).thenReturn(List.of(moduleClass(semester)));
        assertError(
                () -> service.createPlan(principal, 7L, new CreateTimetablePlanRequest()),
                ErrorCode.TIMETABLE_REGISTRATION_STARTED);
        verifyNoInteractions(snapshotService, eventPublisher);
    }

    @Test
    void rejectsOldRevisionBeforeWritingAnything() {
        var plan = plan();
        preparePlanLookup(plan);
        assertError(
                () -> service.applyPlan(principal, 9L, new ApplyTimetablePlanRequest(3L)),
                ErrorCode.TIMETABLE_STALE_PLAN);
        verifyNoInteractions(snapshotService, roomRepository, scheduleRepository, entityManager);
    }

    @Test
    void refusesStaleInputsAndKeepsClassAndOfficialSchedulesUnchanged() {
        var plan = plan();
        var moduleClass = prepareApplying(plan);
        plan.setInputFingerprint("changed");
        assertError(
                () -> service.applyPlan(principal, 9L, new ApplyTimetablePlanRequest(0L)),
                ErrorCode.TIMETABLE_STALE_PLAN);
        assertThat(moduleClass.getStatus()).isEqualTo(ModuleClassStatus.UNSCHEDULED);
        assertThat(moduleClass.getLecturer()).isNull();
        verify(scheduleRepository, never()).saveAll(any());
        verify(scheduleRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void appliesCompleteAllocationWithLecturerAndSchedulesTogether() {
        var plan = plan();
        var moduleClass = prepareApplying(plan);
        var lecturer = com.eduhub.edu_hub.people.domain.model.Lecturer.builder().build();
        lecturer.setId(201L);
        when(entityManager.getReference(com.eduhub.edu_hub.people.domain.model.Lecturer.class, 201L))
                .thenReturn(lecturer);
        var response = service.applyPlan(principal, 9L, new ApplyTimetablePlanRequest(0L));
        assertThat(response.state()).isEqualTo(TimetablePlanState.APPLIED);
        assertThat(moduleClass.getStatus()).isEqualTo(ModuleClassStatus.SCHEDULED);
        assertThat(moduleClass.getLecturer().getId()).isEqualTo(201L);
        var schedules = ArgumentCaptor.forClass(List.class);
        verify(scheduleRepository).saveAllAndFlush(schedules.capture());
        assertThat((List<ClassSchedule>) schedules.getValue()).hasSize(2).allSatisfy(schedule -> {
            assertThat(schedule.getStartDate()).isEqualTo(schedule.getEndDate());
            assertThat(schedule.getLessonOrderEnd() - schedule.getLessonOrderStart() + 1)
                    .isEqualTo(3);
        });
        var ordering = inOrder(peopleFacade, curriculumFacade, roomRepository, snapshotService, scheduleRepository);
        ordering.verify(peopleFacade).lockLecturersForTimetabling(Set.of(201L));
        ordering.verify(curriculumFacade).requireOpenableSubjects(Set.of(1L));
        ordering.verify(roomRepository).findForUpdateByIdAndDeletedFalse(501L);
        ordering.verify(snapshotService).loadProblem(any(), anyList(), eq(Set.of()));
        ordering.verify(scheduleRepository)
                .findAll(
                        ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<ClassSchedule>>any(),
                        any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void treatsRepeatedApplyAsReadWithoutReplacingSchedulesAgain() {
        var plan = plan();
        plan.setState(TimetablePlanState.APPLIED);
        plan.setAppliedAt(LocalDateTime.now(clock));
        preparePlanLookup(plan);
        assertThat(service.applyPlan(principal, 9L, new ApplyTimetablePlanRequest(0L))
                        .state())
                .isEqualTo(TimetablePlanState.APPLIED);
        verifyNoInteractions(snapshotService, roomRepository, scheduleRepository, entityManager);
    }

    private ModuleClass prepareApplying(TimetablePlan plan) {
        var semester = preparePlanLookup(plan);
        var moduleClass = moduleClass(semester);
        when(moduleClassRepository.findAllBySemester_IdOrderByIdAsc(7L)).thenReturn(List.of(moduleClass));
        when(moduleClassRepository.findForUpdateByIdAndDeletedFalse(100L)).thenReturn(Optional.of(moduleClass));
        when(snapshotService.loadProblem(eq(semester), anyList(), eq(Set.of()))).thenReturn(problem);
        var room = Room.builder()
                .roomCode("101-TA1")
                .capacity(80)
                .status(RoomStatus.ACTIVE)
                .build();
        room.setId(501L);
        when(roomRepository.findForUpdateByIdAndDeletedFalse(501L)).thenReturn(Optional.of(room));
        return moduleClass;
    }

    private Semester preparePlanLookup(TimetablePlan plan) {
        var semester = semester();
        when(planRepository.findSemesterIdById(9L)).thenReturn(Optional.of(7L));
        when(semesterRepository.findForUpdateByIdAndDeletedFalse(7L)).thenReturn(Optional.of(semester));
        when(planRepository.findForUpdateById(9L)).thenReturn(Optional.of(plan));
        return semester;
    }

    private TimetablePlan plan() {
        var plan = TimetablePlan.builder()
                .semesterId(7L)
                .state(TimetablePlanState.FEASIBLE)
                .hardPenalty(0L)
                .softPenalty(0L)
                .problemJson(codec.write(problem))
                .inputFingerprint(codec.fingerprint(problem))
                .allocationJson(codec.write(TimetableTestData.allocation(problem)))
                .diagnosticsJson("[]")
                .build();
        plan.setId(9L);
        return plan;
    }

    private Semester semester() {
        var semester = Semester.builder()
                .semesterCode("HK_1_2027_2028")
                .startDate(problem.startDate())
                .endDate(problem.endDate())
                .registrationStart(problem.registrationStart())
                .registrationEnd(problem.registrationEnd())
                .build();
        semester.setId(7L);
        return semester;
    }

    private ModuleClass moduleClass(Semester semester) {
        var subject =
                Subject.builder().subjectCode("CS1").totalTeachingPeriods(6).build();
        subject.setId(1L);
        var moduleClass = ModuleClass.builder()
                .moduleClassCode("MC_1_0")
                .subject(subject)
                .semester(semester)
                .maxCapacity(60)
                .status(ModuleClassStatus.UNSCHEDULED)
                .build();
        moduleClass.setId(100L);
        return moduleClass;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(TimetableException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expected));
    }
}
