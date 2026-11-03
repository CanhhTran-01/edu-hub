package com.eduhub.edu_hub.scheduling.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.scheduling.api.dto.CreateSemesterRequest;
import com.eduhub.edu_hub.scheduling.api.dto.SemesterSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.UpdateSemesterRequest;
import com.eduhub.edu_hub.scheduling.application.exception.SemesterConflictException;
import com.eduhub.edu_hub.scheduling.domain.entity.Semester;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.enums.SemesterStatus;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class SemesterApplicationServiceTest {
    // UTC vẫn là 31/08, nhưng ngày nghiệp vụ ở Việt Nam đã là 01/09.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-31T17:30:00Z"), ZoneOffset.UTC);

    @Mock
    private SemesterRepository repository;

    @Mock
    private ModuleClassRepository moduleClassRepository;

    private SemesterApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SemesterApplicationService(repository, moduleClassRepository, CLOCK);
    }

    @Test
    void createsTrimmedNameAndGeneratedCodeWithUtcAuditTimes() {
        var request = validRequest();
        request.setSemesterName("  Học kì 1 năm học 2026-2027  ");
        when(repository.saveAndFlush(any(Semester.class))).thenAnswer(invocation -> {
            Semester semester = invocation.getArgument(0);
            semester.setId(1L);
            return semester;
        });

        var result = service.createSemester(request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSemesterName()).isEqualTo("Học kì 1 năm học 2026-2027");
        assertThat(result.getSemesterCode()).isEqualTo("HK_1_2026_2027");
        assertThat(result.getStatus()).isEqualTo(SemesterStatus.ONGOING);
        assertThat(result.getIsLocked()).isFalse();
        assertThat(result.getLockedGrade()).isFalse();
        var captor = ArgumentCaptor.forClass(Semester.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().isDeleted()).isFalse();
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-08-31T17:30:00"));
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(captor.getValue().getCreatedAt());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "HK_1 - 2026-2027",
                "Học kì 0 năm học 2026-2027",
                "Học kì 01 năm học 2026-2027",
                "Học kì 1 năm học 2026-2028",
                "Học kì 1 năm học 0000-0001"
            })
    void rejectsInvalidNameAndNonConsecutiveAcademicYears(String name) {
        var request = validRequest();
        request.setSemesterName(name);

        assertBusinessError(() -> service.createSemester(request), ErrorCode.INVALID_SEMESTER_NAME);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @CsvSource({"2026-09-01,2026-09-01", "2026-09-02,2026-09-01"})
    void rejectsEqualOrReversedStudyDates(LocalDate start, LocalDate end) {
        var request = validRequest();
        request.setStartDate(start);
        request.setEndDate(end);

        assertBusinessError(() -> service.createSemester(request), ErrorCode.INVALID_SEMESTER_DATES);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @CsvSource({"2026-08-20,2026-08-20", "2026-08-21,2026-08-20", "2026-08-20,2026-09-01", "2026-08-20,2026-09-02"})
    void rejectsInvalidRegistrationOrderOrClosingOnOrAfterStart(LocalDate open, LocalDate close) {
        var request = validRequest();
        request.setRegistrationStart(open);
        request.setRegistrationEnd(close);

        assertBusinessError(() -> service.createSemester(request), ErrorCode.INVALID_REGISTRATION_PERIOD);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsDuplicateNameIncludingSoftDeletedNames() {
        when(repository.existsBySemesterName("Học kì 1 năm học 2026-2027")).thenReturn(true);

        assertBusinessError(() -> service.createSemester(validRequest()), ErrorCode.DUPLICATE_SEMESTER_NAME);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void studyConflictIncludesSemesterNameAndDatesAndDoesNotSave() {
        var conflict = semester(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-01"));
        conflict.setSemesterName("Học kì 3 năm học 2025-2026");
        when(repository.findAll(ArgumentMatchers.<Specification<Semester>>any(), eq(Sort.by("id"))))
                .thenReturn(List.of(conflict));

        assertThatThrownBy(() -> service.createSemester(validRequest()))
                .isInstanceOfSatisfying(SemesterConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SEMESTER_DATE_OVERLAP);
                    assertThat(exception.getErrors()).singleElement().satisfies(error -> {
                        assertThat(error.getField()).isEqualTo("studyPeriod");
                        assertThat(error.getMessage())
                                .contains("Học kì 3 năm học 2025-2026", "2026-08-01", "2026-09-01");
                    });
                });
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void registrationConflictIsCheckedSeparatelyAndDoesNotSave() {
        var conflict = semester(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-05-01"));
        conflict.setSemesterName("Học kì 2 năm học 2026-2027");
        when(repository.findAll(ArgumentMatchers.<Specification<Semester>>any(), eq(Sort.by("id"))))
                .thenReturn(List.of(), List.of(conflict));

        assertThatThrownBy(() -> service.createSemester(validRequest()))
                .isInstanceOfSatisfying(SemesterConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SEMESTER_REGISTRATION_OVERLAP);
                    assertThat(exception.getErrors()).singleElement().satisfies(error -> {
                        assertThat(error.getField()).isEqualTo("registrationPeriod");
                        assertThat(error.getMessage())
                                .contains("Học kì 2 năm học 2026-2027", "2026-08-10", "2026-08-20");
                    });
                });
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @CsvSource({
        "2026-09-02,2026-12-31,UPCOMING",
        "2026-09-01,2026-12-31,ONGOING",
        "2026-06-01,2026-09-01,ONGOING",
        "2026-06-01,2026-08-31,ENDED"
    })
    void derivesStatusInVietnamIncludingBothStudyBoundaryDays(LocalDate start, LocalDate end, SemesterStatus expected) {
        when(repository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(semester(start, end)));

        assertThat(service.getSemesterById(1L).getStatus()).isEqualTo(expected);
    }

    @Test
    void updatesNameCodeAndDatesWhilePreservingIdentityAndLockFlags() {
        var semester = semester(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-12-31"));
        semester.setLockedGrade(true);
        semester.setCreatedAt(LocalDateTime.parse("2026-07-01T00:00:00"));
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(semester));
        when(repository.saveAndFlush(semester)).thenReturn(semester);
        var request = validUpdate();
        request.setSemesterName("Học kì 2 năm học 2026-2027");

        var result = service.updateSemester(1L, request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSemesterCode()).isEqualTo("HK_2_2026_2027");
        assertThat(result.getLockedGrade()).isTrue();
        assertThat(semester.getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-07-01T00:00:00"));
        verify(repository).existsBySemesterNameAndIdNot("Học kì 2 năm học 2026-2027", 1L);
    }

    @Test
    void rejectsDuplicateNameOnUpdateWithoutChangingEntity() {
        var semester = semester(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-12-31"));
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(semester));
        when(repository.existsBySemesterNameAndIdNot("Học kì 2 năm học 2026-2027", 1L))
                .thenReturn(true);
        var request = validUpdate();
        request.setSemesterName("Học kì 2 năm học 2026-2027");

        assertBusinessError(() -> service.updateSemester(1L, request), ErrorCode.DUPLICATE_SEMESTER_NAME);
        assertThat(semester.getSemesterName()).isEqualTo("Học kì 1 năm học 2026-2027");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void cannotReopenAnEndedSemesterBySupplyingFutureDates() {
        var semester = semester(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-08-31"));
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(semester));

        assertBusinessError(() -> service.updateSemester(1L, validUpdate()), ErrorCode.SEMESTER_ENDED);
        assertThat(semester.getEndDate()).isEqualTo(LocalDate.parse("2026-08-31"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void missingOrDeletedSemesterReturnsNotFoundForReadAndUpdate() {
        assertBusinessError(() -> service.getSemesterById(1L), ErrorCode.SEMESTER_NOT_FOUND);
        assertBusinessError(() -> service.updateSemester(1L, validUpdate()), ErrorCode.SEMESTER_NOT_FOUND);
    }

    @Test
    void searchReturnsPaginationAndCalculatedStatus() {
        var request = new SemesterSearchRequest();
        request.setPage(1);
        request.setSize(1);
        var pageable = PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "startDate", "id"));
        var semester = semester(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-12-31"));
        when(repository.findAll(ArgumentMatchers.<Specification<Semester>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(semester), pageable, 3));

        var result = service.searchSemesters(request);

        assertThat(result.getPageNumber()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.isLast()).isFalse();
        assertThat(result.getContent()).singleElement().satisfies(item -> assertThat(item.getStatus())
                .isEqualTo(SemesterStatus.ONGOING));
    }

    @Test
    void changingStudyDatesWithNonCancelledClassesRequiresConfirmationBeforeMutation() {
        var semester = semester(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-12-31"));
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(semester));
        when(moduleClassRepository.existsBySemester_IdAndStatusNot(1L, ModuleClassStatus.CANCELLED))
                .thenReturn(true);
        var request = validUpdate();
        request.setEndDate(LocalDate.parse("2027-01-05"));

        assertBusinessError(
                () -> service.updateSemester(1L, request), ErrorCode.SEMESTER_DATE_CHANGE_CONFIRMATION_REQUIRED);
        assertThat(semester.getEndDate()).isEqualTo(LocalDate.parse("2026-12-31"));
        verify(repository, never()).saveAndFlush(any());

        request.setConfirmDateChange(true);
        when(repository.saveAndFlush(semester)).thenReturn(semester);
        assertThat(service.updateSemester(1L, request).getEndDate()).isEqualTo(LocalDate.parse("2027-01-05"));
    }

    private void assertBusinessError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode error) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(error));
    }

    private CreateSemesterRequest validRequest() {
        var request = new CreateSemesterRequest();
        request.setSemesterName("Học kì 1 năm học 2026-2027");
        request.setStartDate(LocalDate.parse("2026-09-01"));
        request.setEndDate(LocalDate.parse("2026-12-31"));
        request.setRegistrationStart(LocalDate.parse("2026-08-10"));
        request.setRegistrationEnd(LocalDate.parse("2026-08-20"));
        return request;
    }

    private UpdateSemesterRequest validUpdate() {
        var request = new UpdateSemesterRequest();
        request.setSemesterName("Học kì 1 năm học 2026-2027");
        request.setStartDate(LocalDate.parse("2026-09-01"));
        request.setEndDate(LocalDate.parse("2026-12-31"));
        request.setRegistrationStart(LocalDate.parse("2026-08-10"));
        request.setRegistrationEnd(LocalDate.parse("2026-08-20"));
        return request;
    }

    private Semester semester(LocalDate start, LocalDate end) {
        var semester = Semester.builder()
                .semesterName("Học kì 1 năm học 2026-2027")
                .semesterCode("HK_1_2026_2027")
                .startDate(start)
                .endDate(end)
                .registrationStart(LocalDate.parse("2026-08-10"))
                .registrationEnd(LocalDate.parse("2026-08-20"))
                .build();
        semester.setId(1L);
        return semester;
    }
}
