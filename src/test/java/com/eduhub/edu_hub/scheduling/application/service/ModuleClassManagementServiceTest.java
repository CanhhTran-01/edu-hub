package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.enrollment.application.facade.dto.ModuleClassUsageResponse;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.scheduling.api.dto.request.CancelModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.UpdateModuleClassRequest;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassConflictException;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.policy.OpeningDemandPolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModuleClassManagementServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-02T18:00:00Z"),
            new TimeConfig().applicationClock().getZone());

    @Mock
    private SemesterRepository semesters;

    @Mock
    private ModuleClassRepository classes;

    @Mock
    private CurriculumFacade.ClassOpening curriculum;

    @Mock
    private PeopleFacade.Scheduling people;

    @Mock
    private AcademicPlanningSnapshotService academicPlanningSnapshotService;

    @Mock
    private ModuleClassEnrollmentQueryService moduleClassEnrollmentQueryService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ClassScheduleQueryService classScheduleQueryService;

    @Mock
    private com.eduhub.edu_hub.scheduling.domain.repository.ClassScheduleRepository classScheduleRepository;

    @Mock
    private com.eduhub.edu_hub.scheduling.domain.repository.RoomRepository roomRepository;

    private ModuleClassApplicationService service;
    private Semester semester;

    @BeforeEach
    void setUp() {
        service = new ModuleClassApplicationService(
                semesters,
                classes,
                curriculum,
                people,
                academicPlanningSnapshotService,
                moduleClassEnrollmentQueryService,
                new OpeningDemandPolicy(),
                entityManager,
                CLOCK,
                classScheduleQueryService,
                classScheduleRepository,
                roomRepository,
                com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData.configuredService());
        semester = Semester.builder()
                .semesterCode("HK_1_2026_2027")
                .startDate(LocalDate.parse("2026-08-11"))
                .endDate(LocalDate.parse("2027-01-05"))
                .build();
        semester.setId(9L);
    }

    @Test
    void changesCapacityAndKeepsLecturerClassIdentityStatusAndCreatedTime() {
        var item = prepare(ModuleClassStatus.UNSCHEDULED, 20);
        prepareResponse(item);
        var request = update(40);

        var result = service.updateModuleClassCapacity(101L, request);

        assertThat(result.getLecturerId()).isEqualTo(5001L);
        assertThat(result.getLecturerName()).isEqualTo("Giảng viên từ People");
        verifyNoInteractions(academicPlanningSnapshotService);
        assertThat(result.getMaxCapacity()).isEqualTo(40);
        assertThat(result.getCurrentSize()).isEqualTo(20);
        assertThat(result.getStatus()).isEqualTo(ModuleClassStatus.UNSCHEDULED);
        assertThat(result.getModuleClassCode()).isEqualTo("MC_9_CS101_001");
        assertThat(result.getSemesterId()).isEqualTo(9L);
        assertThat(result.getSubjectId()).isEqualTo(1L);
        assertThat(result.getClassSequence()).isEqualTo(1);
        assertThat(item.getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-08-01T00:00:00"));
        assertThat(item.getUpdatedAt()).isEqualTo(LocalDateTime.parse("2026-11-02T18:00:00"));
        assertThat(item.isDeleted()).isFalse();
        verifyNoInteractions(moduleClassEnrollmentQueryService);
    }

    @Test
    void reducingCapacityBelowSizeKeepsTheClassUnchanged() {
        var item = prepare(ModuleClassStatus.UNSCHEDULED, 30);

        assertThatThrownBy(() -> service.updateModuleClassCapacity(101L, update(29)))
                .isInstanceOfSatisfying(ModuleClassConflictException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MODULE_CLASS_CAPACITY_BELOW_SIZE);
                    assertThat(error.getErrors()).singleElement().satisfies(detail -> {
                        assertThat(detail.getField()).isEqualTo("maxCapacity");
                        assertThat(detail.getMessage()).contains("30");
                    });
                });
        assertThat(item.getLecturer().getId()).isEqualTo(5001L);
        assertThat(item.getMaxCapacity()).isEqualTo(80);
        verify(classes, never()).saveAndFlush(any());
        verifyNoInteractions(people, curriculum, moduleClassEnrollmentQueryService);
    }

    @ParameterizedTest
    @EnumSource(
            value = ModuleClassStatus.class,
            names = {"UNSCHEDULED", "SCHEDULED", "OPEN"})
    void capacityCanEqualCurrentSizeWithoutChangingLecturerOrStatus(ModuleClassStatus status) {
        var item = prepare(status, 30);
        prepareResponse(item);

        var result = service.updateModuleClassCapacity(101L, update(30));

        assertThat(result.getMaxCapacity()).isEqualTo(30);
        assertThat(result.getLecturerId()).isEqualTo(5001L);
        assertThat(result.getStatus()).isEqualTo(status);
    }

    @Test
    void capacityUpdateDoesNotRevalidateAnExistingInactiveLecturer() {
        var item = prepare(ModuleClassStatus.OPEN, 10, 5009L);
        prepareResponse(item);

        var result = service.updateModuleClassCapacity(101L, update(40));

        assertThat(result.getMaxCapacity()).isEqualTo(40);
        assertThat(result.getLecturerId()).isEqualTo(5009L);
        assertThat(result.getLecturerName()).isEqualTo("Giảng viên từ People");
        assertThat(result.getStatus()).isEqualTo(ModuleClassStatus.OPEN);
    }

    @Test
    void unassignedClassCanChangeCapacityWithoutPeopleData() {
        var item = prepare(ModuleClassStatus.UNSCHEDULED, 0, null);
        prepareResponse(item);

        var result = service.updateModuleClassCapacity(101L, update(40));

        assertThat(result.getMaxCapacity()).isEqualTo(40);
        assertThat(result.getLecturerId()).isNull();
        assertThat(result.getLecturerName()).isNull();
        assertThat(result.getStatus()).isEqualTo(ModuleClassStatus.UNSCHEDULED);
        assertThat(result.getDataSource()).isEqualTo("DATABASE");
        verifyNoInteractions(people);
    }

    @Test
    void cancellationWithACompleteEmptyHistoryKeepsTheClassVisibleAndReservesItsCode() {
        var item = prepare(ModuleClassStatus.UNSCHEDULED, 0, null);
        prepareResponse(item);
        when(moduleClassEnrollmentQueryService.summarizeModuleClassEnrollments(101L))
                .thenReturn(new ModuleClassUsageResponse(true, 0, 0, "DEV_FIXTURE"));

        var result = service.cancelModuleClass(101L, cancelRequest());

        assertThat(result.getStatus()).isEqualTo(ModuleClassStatus.CANCELLED);
        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getModuleClassCode()).isEqualTo("MC_9_CS101_001");
        assertThat(result.getClassSequence()).isEqualTo(1);
        assertThat(item.isDeleted()).isFalse();
        assertThat(item.getCurrentSize()).isZero();
        assertThat(result.getLecturerId()).isNull();
        assertThat(result.getDataSource()).isEqualTo("DATABASE");
        verifyNoInteractions(people);
    }

    @Test
    void anEmptyRosterWithHistoricalEnrollmentsStillCannotBeCancelled() {
        var item = prepare(ModuleClassStatus.UNSCHEDULED, 0);
        when(moduleClassEnrollmentQueryService.summarizeModuleClassEnrollments(101L))
                .thenReturn(new ModuleClassUsageResponse(true, 3, 2, "DEV_FIXTURE"));

        assertThatThrownBy(() -> service.cancelModuleClass(101L, cancelRequest()))
                .isInstanceOfSatisfying(ModuleClassConflictException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MODULE_CLASS_HAS_ENROLLMENTS);
                    assertThat(error.getErrors().get(0).getMessage()).contains("3", "đã hủy/ẩn");
                    assertThat(error.getErrors().get(1).getMessage()).contains("2");
                });
        assertThat(item.getStatus()).isEqualTo(ModuleClassStatus.UNSCHEDULED);
        verify(classes, never()).saveAndFlush(any());
    }

    @Test
    void aNonEmptyRosterBlocksCancellationEvenIfTheHistorySourceIsUnavailable() {
        var item = prepare(ModuleClassStatus.OPEN, 12);
        assertError(() -> service.cancelModuleClass(101L, cancelRequest()), ErrorCode.MODULE_CLASS_HAS_ENROLLMENTS);
        assertThat(item.getStatus()).isEqualTo(ModuleClassStatus.OPEN);
        verifyNoInteractions(moduleClassEnrollmentQueryService);
        verify(classes, never()).saveAndFlush(any());
    }

    @Test
    void incompleteHistoryIsNotTreatedAsZeroEnrollments() {
        var item = prepare(ModuleClassStatus.UNSCHEDULED, 0);
        when(moduleClassEnrollmentQueryService.summarizeModuleClassEnrollments(101L))
                .thenReturn(new ModuleClassUsageResponse(false, 0, 0, "TEST"));
        assertError(
                () -> service.cancelModuleClass(101L, cancelRequest()),
                ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);
        assertThat(item.getStatus()).isEqualTo(ModuleClassStatus.UNSCHEDULED);
        verify(classes, never()).saveAndFlush(any());
    }

    @Test
    void cancelledClassesCannotBeUpdatedOrCancelledAgain() {
        prepare(ModuleClassStatus.CANCELLED, 0);
        assertError(() -> service.updateModuleClassCapacity(101L, update(50)), ErrorCode.MODULE_CLASS_CANCELLED);
        assertError(() -> service.cancelModuleClass(101L, cancelRequest()), ErrorCode.MODULE_CLASS_CANCELLED);
        verify(classes, never()).saveAndFlush(any());
        verifyNoInteractions(people, curriculum, moduleClassEnrollmentQueryService);
    }

    @Test
    void vietnamDateAlreadyAfterEndBlocksBothOperations() {
        semester.setEndDate(LocalDate.parse("2026-11-02"));
        when(classes.findSemesterIdByIdAndDeletedFalse(101L)).thenReturn(Optional.of(9L));
        when(semesters.findForUpdateByIdAndDeletedFalse(9L)).thenReturn(Optional.of(semester));
        assertError(() -> service.updateModuleClassCapacity(101L, update(50)), ErrorCode.SEMESTER_ENDED);
        assertError(() -> service.cancelModuleClass(101L, cancelRequest()), ErrorCode.SEMESTER_ENDED);
        verify(classes, never()).findForUpdateByIdAndDeletedFalse(anyLong());
    }

    @Test
    void lockedSemesterBlocksBothOperations() {
        semester.setLocked(true);
        when(classes.findSemesterIdByIdAndDeletedFalse(101L)).thenReturn(Optional.of(9L));
        when(semesters.findForUpdateByIdAndDeletedFalse(9L)).thenReturn(Optional.of(semester));
        assertError(() -> service.updateModuleClassCapacity(101L, update(50)), ErrorCode.SEMESTER_LOCKED);
        assertError(() -> service.cancelModuleClass(101L, cancelRequest()), ErrorCode.SEMESTER_LOCKED);
        verify(classes, never()).findForUpdateByIdAndDeletedFalse(anyLong());
    }

    @Test
    void missingOrHiddenClassIsNotFound() {
        assertError(() -> service.updateModuleClassCapacity(101L, update(50)), ErrorCode.MODULE_CLASS_NOT_FOUND);
        assertError(() -> service.cancelModuleClass(101L, cancelRequest()), ErrorCode.MODULE_CLASS_NOT_FOUND);
        verifyNoInteractions(semesters, people, curriculum, moduleClassEnrollmentQueryService);
    }

    private ModuleClass prepare(ModuleClassStatus status, int size) {
        return prepare(status, size, 5001L);
    }

    @Test
    void increasingCapacityMustStillFitEveryScheduledRoom() {
        var item = prepare(ModuleClassStatus.SCHEDULED, 20);
        item.changeCapacity(40);
        when(people.findLecturersByIds(Set.of(5001L))).thenReturn(List.of());
        when(curriculum.findSubjectsByIds(Set.of(1L))).thenReturn(List.of());
        var room = com.eduhub.edu_hub.scheduling.domain.model.Room.builder()
                .roomCode("101-TA1")
                .capacity(50)
                .build();
        room.setId(501L);
        var schedule = com.eduhub.edu_hub.scheduling.domain.model.ClassSchedule.builder()
                .moduleClass(item)
                .room(room)
                .build();
        when(classScheduleRepository.findAll(
                        org.mockito.ArgumentMatchers
                                .<org.springframework.data.jpa.domain.Specification<
                                                com.eduhub.edu_hub.scheduling.domain.model.ClassSchedule>>
                                        any(),
                        any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(schedule));
        when(roomRepository.findForUpdateByIdAndDeletedFalse(501L)).thenReturn(Optional.of(room));
        assertError(() -> service.updateModuleClassCapacity(101L, update(60)), ErrorCode.ROOM_CAPACITY_BELOW_CLASS);
        assertThat(item.getMaxCapacity()).isEqualTo(40);
        verify(classes, never()).saveAndFlush(any());
        verify(entityManager).refresh(room, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    private ModuleClass prepare(ModuleClassStatus status, int size, Long lecturerId) {
        var subject = Subject.builder().build();
        subject.setId(1L);
        Lecturer lecturer = null;
        if (lecturerId != null) {
            lecturer = new Lecturer();
            lecturer.setId(lecturerId);
        }
        var item = ModuleClass.builder()
                .semester(semester)
                .subject(subject)
                .lecturer(lecturer)
                .moduleClassCode("MC_9_CS101_001")
                .classSequence(1)
                .maxCapacity(80)
                .currentSize(size)
                .status(status)
                .build();
        item.setId(101L);
        item.setCreatedAt(LocalDateTime.parse("2026-08-01T00:00:00"));
        when(classes.findSemesterIdByIdAndDeletedFalse(101L)).thenReturn(Optional.of(9L));
        when(semesters.findForUpdateByIdAndDeletedFalse(9L)).thenReturn(Optional.of(semester));
        when(classes.findForUpdateByIdAndDeletedFalse(101L)).thenReturn(Optional.of(item));
        return item;
    }

    private void prepareResponse(ModuleClass item) {
        if (item.getLecturer() != null)
            when(people.findLecturersByIds(Set.of(item.getLecturer().getId())))
                    .thenReturn(List.of(LecturerResponse.builder()
                            .id(item.getLecturer().getId())
                            .fullName("Giảng viên từ People")
                            .status(
                                    item.getLecturer().getId().equals(5009L)
                                            ? LecturerStatus.INACTIVE
                                            : LecturerStatus.ACTIVE)
                            .build()));
        when(curriculum.findSubjectsByIds(Set.of(1L)))
                .thenReturn(List.of(SubjectSimpleResponse.builder()
                        .id(1L)
                        .subjectCode("CS101")
                        .subjectName("Nhập môn lập trình")
                        .credits(3)
                        .build()));
        when(classes.saveAndFlush(item)).thenReturn(item);
    }

    private UpdateModuleClassRequest update(Integer capacity) {
        var request = new UpdateModuleClassRequest();
        request.setMaxCapacity(capacity);
        request.setReason("Điều chỉnh quy mô lớp");
        return request;
    }

    private CancelModuleClassRequest cancelRequest() {
        var request = new CancelModuleClassRequest();
        request.setReason("Không đủ nhu cầu mở lớp");
        return request;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                .isEqualTo(expected));
    }
}
