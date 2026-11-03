package com.eduhub.edu_hub.scheduling.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.CurriculumFacade;
import com.eduhub.edu_hub.scheduling.api.dto.CancelModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.UpdateModuleClassRequest;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassConflictException;
import com.eduhub.edu_hub.scheduling.application.port.AcademicPlanningDataSource;
import com.eduhub.edu_hub.scheduling.application.port.ModuleClassEnrollmentQuery;
import com.eduhub.edu_hub.scheduling.application.port.ModuleClassEnrollmentQuery.Usage;
import com.eduhub.edu_hub.scheduling.domain.entity.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.entity.Semester;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.scheduling.infrastructure.DevAcademicPlanningDataSource;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
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
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T18:00:00Z"), ZoneOffset.UTC);

    @Mock
    private SemesterRepository semesters;

    @Mock
    private ModuleClassRepository classes;

    @Mock
    private CurriculumFacade.ClassOpening curriculum;

    @Mock
    private AcademicPlanningDataSource people;

    @Mock
    private ModuleClassEnrollmentQuery enrollments;

    private ModuleClassApplicationService service;
    private Semester semester;

    @BeforeEach
    void setUp() {
        service = new ModuleClassApplicationService(
                semesters, classes, curriculum, people, enrollments, new OpeningDemandCalculator(), CLOCK);
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

        var result = service.update(101L, request);

        assertThat(result.getLecturerId()).isEqualTo(5001L);
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
        verifyNoInteractions(enrollments);
    }

    @Test
    void reducingCapacityBelowSizeKeepsTheClassUnchanged() {
        var item = prepare(ModuleClassStatus.UNSCHEDULED, 30);

        assertThatThrownBy(() -> service.update(101L, update(29)))
                .isInstanceOfSatisfying(ModuleClassConflictException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MODULE_CLASS_CAPACITY_BELOW_SIZE);
                    assertThat(error.getErrors()).singleElement().satisfies(detail -> {
                        assertThat(detail.getField()).isEqualTo("maxCapacity");
                        assertThat(detail.getMessage()).contains("30");
                    });
                });
        assertThat(item.getLecturerId()).isEqualTo(5001L);
        assertThat(item.getMaxCapacity()).isEqualTo(80);
        verify(classes, never()).saveAndFlush(any());
        verifyNoInteractions(people, curriculum, enrollments);
    }

    @ParameterizedTest
    @EnumSource(
            value = ModuleClassStatus.class,
            names = {"UNSCHEDULED", "SCHEDULED", "OPEN"})
    void capacityCanEqualCurrentSizeWithoutChangingLecturerOrStatus(ModuleClassStatus status) {
        var item = prepare(status, 30);
        prepareResponse(item);

        var result = service.update(101L, update(30));

        assertThat(result.getMaxCapacity()).isEqualTo(30);
        assertThat(result.getLecturerId()).isEqualTo(5001L);
        assertThat(result.getStatus()).isEqualTo(status);
    }

    @Test
    void capacityUpdateDoesNotRevalidateAnExistingInactiveLecturer() {
        var item = prepare(ModuleClassStatus.OPEN, 10, 5009L);
        prepareResponse(item);

        var result = service.update(101L, update(40));

        assertThat(result.getMaxCapacity()).isEqualTo(40);
        assertThat(result.getLecturerId()).isEqualTo(5009L);
        assertThat(result.getStatus()).isEqualTo(ModuleClassStatus.OPEN);
    }

    @Test
    void unassignedClassCanChangeCapacityWithoutPeopleData() {
        var item = prepare(ModuleClassStatus.UNSCHEDULED, 0, null);
        prepareResponse(item);

        var result = service.update(101L, update(40));

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
        when(enrollments.summarize(101L, item.getModuleClassCode())).thenReturn(new Usage(true, 0, 0, "DEV_FIXTURE"));

        var result = service.cancel(101L, cancelRequest());

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
        when(enrollments.summarize(101L, item.getModuleClassCode())).thenReturn(new Usage(true, 3, 2, "DEV_FIXTURE"));

        assertThatThrownBy(() -> service.cancel(101L, cancelRequest()))
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
        assertError(() -> service.cancel(101L, cancelRequest()), ErrorCode.MODULE_CLASS_HAS_ENROLLMENTS);
        assertThat(item.getStatus()).isEqualTo(ModuleClassStatus.OPEN);
        verifyNoInteractions(enrollments);
        verify(classes, never()).saveAndFlush(any());
    }

    @Test
    void incompleteHistoryIsNotTreatedAsZeroEnrollments() {
        var item = prepare(ModuleClassStatus.UNSCHEDULED, 0);
        when(enrollments.summarize(101L, item.getModuleClassCode())).thenReturn(new Usage(false, 0, 0, "TEST"));
        assertError(() -> service.cancel(101L, cancelRequest()), ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);
        assertThat(item.getStatus()).isEqualTo(ModuleClassStatus.UNSCHEDULED);
        verify(classes, never()).saveAndFlush(any());
    }

    @Test
    void cancelledClassesCannotBeUpdatedOrCancelledAgain() {
        prepare(ModuleClassStatus.CANCELLED, 0);
        assertError(() -> service.update(101L, update(50)), ErrorCode.MODULE_CLASS_CANCELLED);
        assertError(() -> service.cancel(101L, cancelRequest()), ErrorCode.MODULE_CLASS_CANCELLED);
        verify(classes, never()).saveAndFlush(any());
        verifyNoInteractions(people, curriculum, enrollments);
    }

    @Test
    void vietnamDateAlreadyAfterEndBlocksBothOperations() {
        semester.setEndDate(LocalDate.parse("2026-11-02"));
        when(classes.findSemesterIdByIdAndDeletedFalse(101L)).thenReturn(Optional.of(9L));
        when(semesters.findForUpdateByIdAndDeletedFalse(9L)).thenReturn(Optional.of(semester));
        assertError(() -> service.update(101L, update(50)), ErrorCode.SEMESTER_ENDED);
        assertError(() -> service.cancel(101L, cancelRequest()), ErrorCode.SEMESTER_ENDED);
        verify(classes, never()).findForUpdateByIdAndDeletedFalse(anyLong());
    }

    @Test
    void lockedSemesterBlocksBothOperations() {
        semester.setLocked(true);
        when(classes.findSemesterIdByIdAndDeletedFalse(101L)).thenReturn(Optional.of(9L));
        when(semesters.findForUpdateByIdAndDeletedFalse(9L)).thenReturn(Optional.of(semester));
        assertError(() -> service.update(101L, update(50)), ErrorCode.SEMESTER_LOCKED);
        assertError(() -> service.cancel(101L, cancelRequest()), ErrorCode.SEMESTER_LOCKED);
        verify(classes, never()).findForUpdateByIdAndDeletedFalse(anyLong());
    }

    @Test
    void missingOrHiddenClassIsNotFound() {
        assertError(() -> service.update(101L, update(50)), ErrorCode.MODULE_CLASS_NOT_FOUND);
        assertError(() -> service.cancel(101L, cancelRequest()), ErrorCode.MODULE_CLASS_NOT_FOUND);
        verifyNoInteractions(semesters, people, curriculum, enrollments);
    }

    private ModuleClass prepare(ModuleClassStatus status, int size) {
        return prepare(status, size, 5001L);
    }

    private ModuleClass prepare(ModuleClassStatus status, int size, Long lecturerId) {
        var item = ModuleClass.builder()
                .semester(semester)
                .subjectId(1L)
                .lecturerId(lecturerId)
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
        if (item.getLecturerId() != null)
            when(people.snapshot()).thenReturn(new DevAcademicPlanningDataSource().snapshot());
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
