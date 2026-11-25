package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassPublicationRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassConflictException;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.repository.*;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ModuleClassPublicationServiceTest {
    @Mock
    SemesterRepository semesterRepository;

    @Mock
    ModuleClassRepository moduleClassRepository;

    @Mock
    ClassScheduleRepository scheduleRepository;

    @Mock
    RoomRepository roomRepository;

    @Mock
    TimetableSnapshotService snapshotService;

    @Mock
    ModuleClassApplicationService moduleClassService;

    @Mock
    IdentityFacade identityFacade;

    @Mock
    PeopleFacade.Scheduling peopleFacade;

    @Mock
    CurriculumFacade.ClassOpening curriculumFacade;

    @Mock
    EnrollmentFacade.RegistrationUsage enrollmentUsage;

    @Mock
    EntityManager entityManager;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final Jwt principal =
            Jwt.withTokenValue("token").header("alg", "HS256").subject("admin").build();
    private ModuleClassPublicationService service;
    private Semester semester;

    @BeforeEach
    void setUp() {
        service = new ModuleClassPublicationService(
                semesterRepository,
                moduleClassRepository,
                scheduleRepository,
                roomRepository,
                snapshotService,
                moduleClassService,
                identityFacade,
                peopleFacade,
                curriculumFacade,
                enrollmentUsage,
                entityManager,
                clock);
        semester = Semester.builder()
                .registrationStart(LocalDate.of(2026, 11, 20))
                .registrationEnd(LocalDate.of(2026, 11, 30))
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2027, 3, 1))
                .build();
        semester.setId(7L);
    }

    @Test
    void requiresAuthenticatedActorBeforeReadingSemester() {
        assertThatThrownBy(() -> service.publish(null, 7L, all()))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(semesterRepository);
    }

    @Test
    void rejectsWholeSelectionWhenOneClassHasNoLecturer() {
        var first = moduleClass(100L, ModuleClassStatus.SCHEDULED, true);
        var second = moduleClass(101L, ModuleClassStatus.SCHEDULED, false);
        prepare(first, second);
        assertThatThrownBy(() -> service.publish(principal, 7L, all()))
                .isInstanceOf(ModuleClassConflictException.class);
        assertThat(first.getStatus()).isEqualTo(ModuleClassStatus.SCHEDULED);
        assertThat(second.getStatus()).isEqualTo(ModuleClassStatus.SCHEDULED);
        verifyNoInteractions(snapshotService);
        verify(moduleClassRepository, never()).flush();
    }

    @Test
    void publishesOnlySelectedScheduledClassesAfterRecheckingOfficialSchedule() {
        var selected = moduleClass(100L, ModuleClassStatus.SCHEDULED, true);
        prepare(selected);
        when(snapshotService.loadProblem(eq(semester), eq(List.of(selected)), eq(Set.of())))
                .thenReturn(problem(100L));
        when(moduleClassService.findModuleClassById(100L)).thenAnswer(invocation -> ModuleClassResponse.builder()
                .id(100L)
                .status(selected.getStatus())
                .build());
        var response = service.publish(principal, 7L, all());
        assertThat(response.changedClassCount()).isEqualTo(1);
        assertThat(selected.getStatus()).isEqualTo(ModuleClassStatus.OPEN);
        assertThat(response.moduleClasses().getFirst().getStatus()).isEqualTo(ModuleClassStatus.OPEN);
    }

    @Test
    void missingOfficialSessionsPreventPublication() {
        var selected = moduleClass(100L, ModuleClassStatus.SCHEDULED, true);
        prepare(selected);
        var original = problem(100L);
        var incomplete = new TimetableProblem(
                original.semesterId(),
                original.startDate(),
                original.endDate(),
                original.registrationStart(),
                original.registrationEnd(),
                original.classes(),
                original.lecturers(),
                original.rooms(),
                List.of(),
                List.of(),
                Set.of(),
                null);
        when(snapshotService.loadProblem(eq(semester), eq(List.of(selected)), eq(Set.of())))
                .thenReturn(incomplete);
        assertThatThrownBy(() -> service.publish(principal, 7L, all()))
                .isInstanceOf(ModuleClassConflictException.class);
        assertThat(selected.getStatus()).isEqualTo(ModuleClassStatus.SCHEDULED);
    }

    @Test
    void withdrawsAfterAllRegistrationsWereDroppedWithoutDeletingSchedule() {
        var selected = moduleClass(100L, ModuleClassStatus.OPEN, true);
        prepare(selected);
        when(moduleClassService.findModuleClassById(100L))
                .thenReturn(ModuleClassResponse.builder().id(100L).build());
        assertThat(service.withdraw(principal, 7L, all()).changedClassCount()).isEqualTo(1);
        assertThat(selected.getStatus()).isEqualTo(ModuleClassStatus.SCHEDULED);
        assertThat(selected.getLecturerId()).isNotNull();
        verify(enrollmentUsage).countActiveRegistrations(100L);
        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void oneActiveRegistrationBlocksWithdrawalOfEntireSelection() {
        var first = moduleClass(100L, ModuleClassStatus.OPEN, true);
        var second = moduleClass(101L, ModuleClassStatus.OPEN, true);
        prepare(first, second);
        when(enrollmentUsage.countActiveRegistrations(100L)).thenReturn(0L);
        when(enrollmentUsage.countActiveRegistrations(101L)).thenReturn(1L);
        assertThatThrownBy(() -> service.withdraw(principal, 7L, all()))
                .isInstanceOf(ModuleClassConflictException.class);
        assertThat(first.getStatus()).isEqualTo(ModuleClassStatus.OPEN);
        assertThat(second.getStatus()).isEqualTo(ModuleClassStatus.OPEN);
    }

    private void prepare(ModuleClass... classes) {
        when(identityFacade.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(1L).build());
        when(semesterRepository.findForUpdateByIdAndDeletedFalse(7L)).thenReturn(Optional.of(semester));
        when(moduleClassRepository.findAllBySemester_IdOrderByIdAsc(7L)).thenReturn(List.of(classes));
        for (var moduleClass : classes)
            when(moduleClassRepository.findForUpdateByIdAndDeletedFalse(moduleClass.getId()))
                    .thenReturn(Optional.of(moduleClass));
    }

    private ModuleClass moduleClass(Long classId, ModuleClassStatus status, boolean hasLecturer) {
        var subject = Subject.builder().build();
        subject.setId(5L);
        var lecturer = new Lecturer();
        lecturer.setId(8L);
        var moduleClass = ModuleClass.builder()
                .semester(semester)
                .subjectId(subject.getId())
                .moduleClassCode("MC" + classId)
                .lecturerId(hasLecturer ? lecturer.getId() : null)
                .maxCapacity(40)
                .currentSize(0)
                .status(status)
                .build();
        moduleClass.setId(classId);
        return moduleClass;
    }

    private ModuleClassPublicationRequest all() {
        var request = new ModuleClassPublicationRequest();
        request.setSelectAll(true);
        return request;
    }

    private TimetableProblem problem(Long classId) {
        var date = LocalDate.of(2026, 12, 1);
        return new TimetableProblem(
                7L,
                semester.getStartDate(),
                semester.getEndDate(),
                semester.getRegistrationStart(),
                semester.getRegistrationEnd(),
                List.of(new TimetableProblem.ClassRequirement(
                        classId, "MC" + classId, 5L, "CS5", 3, 40, 0, ModuleClassStatus.SCHEDULED, 8L)),
                List.of(new TimetableProblem.LecturerAvailability(8L, "Giảng viên", Set.of(5L))),
                List.of(new TimetableProblem.RoomAvailability(9L, "101-A", 40)),
                List.of(),
                List.of(new TimetableProblem.ExistingSession(1L, classId, 8L, 9L, 3, 1, 3, date, date)),
                Set.of(),
                null);
    }
}
