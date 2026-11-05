package com.eduhub.edu_hub.scheduling.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateModuleClassesRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.SubjectClassesRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassOpeningException;
import com.eduhub.edu_hub.scheduling.application.port.AcademicPlanningDataSource;
import com.eduhub.edu_hub.scheduling.application.port.ModuleClassEnrollmentQuery;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class ModuleClassApplicationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private SemesterRepository semesters;

    @Mock
    private ModuleClassRepository classes;

    @Mock
    private CurriculumFacade.ClassOpening curriculum;

    @Mock
    private PeopleFacade.Scheduling people;

    @Mock
    private AcademicPlanningDataSource dataSource;

    @Mock
    private ModuleClassEnrollmentQuery enrollmentQuery;

    @Mock
    private EntityManager entityManager;

    private ModuleClassApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ModuleClassApplicationService(
                semesters,
                classes,
                curriculum,
                people,
                dataSource,
                enrollmentQuery,
                new OpeningDemandCalculator(),
                entityManager,
                CLOCK);
    }

    @Test
    void createsMultipleSubjectsAtomicallyWithGeneratedCodesAndUnscheduledEmptyClasses() {
        prepare(semester());
        var request = batch(subjectRequest(1, 80, 50), subjectRequest(2, 30));
        when(classes.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<ModuleClass> saved = invocation.getArgument(0);
            for (int i = 0; i < saved.size(); i++) saved.get(i).setId(100L + i);
            return saved;
        });

        var result = service.createBatch(7L, request);

        assertThat(result)
                .extracting(ModuleClassResponse::getModuleClassCode)
                .containsExactly("MC_7_CS101_001", "MC_7_CS101_002", "MC_7_CS102_001");
        assertThat(result).allSatisfy(item -> {
            assertThat(item.getStatus()).isEqualTo(ModuleClassStatus.UNSCHEDULED);
            assertThat(item.getCurrentSize()).isZero();
            assertThat(item.getSchedules()).isEmpty();
            assertThat(item.getLecturerId()).isNull();
            assertThat(item.getLecturerName()).isNull();
            assertThat(item.getDataSource()).isEqualTo("DATABASE");
        });
        assertThat(result).extracting(ModuleClassResponse::getMaxCapacity).containsExactly(80, 50, 30);
        verifyNoInteractions(people, dataSource, enrollmentQuery);
        verify(semesters).findForUpdateByIdAndDeletedFalse(7L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModuleClass>> saved = ArgumentCaptor.forClass(List.class);
        verify(classes).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).allSatisfy(item -> {
            assertThat(item.isDeleted()).isFalse();
            assertThat(item.getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-11-02T10:00:00"));
        });
    }

    @Test
    void warningForAnySubjectPreventsSavingTheEntireBatchAndGivesItsField() {
        var semester = semester();
        prepare(semester);
        var existing = historical(semester, 1, 4, ModuleClassStatus.UNSCHEDULED, true);
        when(classes.findAllBySemester_IdOrderByIdAsc(7L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createBatch(7L, batch(subjectRequest(2, 80), subjectRequest(1, 80))))
                .isInstanceOfSatisfying(ModuleClassOpeningException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MODULE_CLASS_CONFIRMATION_REQUIRED);
                    assertThat(error.getErrors()).singleElement().satisfies(field -> {
                        assertThat(field.getField()).isEqualTo("subjects[1].subjectId");
                        assertThat(field.getMessage()).contains("CS101", "1 lớp");
                    });
                });
        verify(classes, never()).saveAllAndFlush(anyList());
    }

    @Test
    void confirmationContinuesSequenceAcrossCancelledAndSoftDeletedHistory() {
        var semester = semester();
        prepare(semester);
        when(classes.findAllBySemester_IdOrderByIdAsc(7L))
                .thenReturn(List.of(
                        historical(semester, 1, 2, ModuleClassStatus.OPEN, false),
                        historical(semester, 1, 9, ModuleClassStatus.CANCELLED, true)));
        when(classes.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = subjectRequest(1, 80);
        request.setConfirmAdditionalClasses(true);

        assertThat(service.createBatch(7L, batch(request))).singleElement().satisfies(item -> {
            assertThat(item.getClassSequence()).isEqualTo(10);
            assertThat(item.getModuleClassCode()).isEqualTo("MC_7_CS101_010");
        });
    }

    @Test
    void onlyCancelledClassesNeedNoConfirmationButDoReserveTheirSequence() {
        var semester = semester();
        prepare(semester);
        when(classes.findAllBySemester_IdOrderByIdAsc(7L))
                .thenReturn(List.of(historical(semester, 1, 3, ModuleClassStatus.CANCELLED, false)));
        when(classes.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.createBatch(7L, batch(subjectRequest(1, 80))))
                .singleElement()
                .satisfies(item -> assertThat(item.getClassSequence()).isEqualTo(4));
    }

    @Test
    void duplicateSubjectsAreRejectedRatherThanSilentlyMerged() {
        assertError(
                () -> service.createBatch(7L, batch(subjectRequest(1, 80), subjectRequest(1, 80))),
                ErrorCode.DUPLICATE_BATCH_SUBJECT);
        verifyNoInteractions(semesters, classes, curriculum, dataSource);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 81})
    void capacityOutsideOneToEightyIsRejected(int capacity) {
        var subject = subjectRequest(1, 80);
        subject.getClasses().getFirst().setMaxCapacity(capacity);
        assertError(() -> service.createBatch(7L, batch(subject)), ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(semesters, classes, curriculum, dataSource);
    }

    @Test
    void lockedSemesterRejectsClassOpeningBeforeCallingOtherModules() {
        var semester = semester();
        semester.setLocked(true);
        when(semesters.findForUpdateByIdAndDeletedFalse(7L)).thenReturn(Optional.of(semester));
        assertError(() -> service.createBatch(7L, batch(subjectRequest(1, 80))), ErrorCode.SEMESTER_LOCKED);
        verifyNoInteractions(classes, curriculum, dataSource);
    }

    @Test
    void semesterEndsOnlyAfterItsEndDateInVietnam() {
        var semester = semester();
        semester.setEndDate(LocalDate.parse("2026-11-01"));
        when(semesters.findForUpdateByIdAndDeletedFalse(7L)).thenReturn(Optional.of(semester));
        assertError(() -> service.createBatch(7L, batch(subjectRequest(1, 80))), ErrorCode.SEMESTER_ENDED);
        verifyNoInteractions(classes, curriculum, dataSource);
    }

    @Test
    void missingSemesterAndClassReturnBusinessNotFound() {
        assertError(() -> service.createBatch(7L, batch(subjectRequest(1, 80))), ErrorCode.SEMESTER_NOT_FOUND);
        assertError(() -> service.findById(99L), ErrorCode.MODULE_CLASS_NOT_FOUND);
    }

    private void prepare(Semester semester) {
        lenient().when(entityManager.getReference(eq(Subject.class), anyLong())).thenAnswer(invocation -> {
            var subject = Subject.builder().build();
            subject.setId(invocation.getArgument(1));
            return subject;
        });
        when(semesters.findForUpdateByIdAndDeletedFalse(7L)).thenReturn(Optional.of(semester));
        when(curriculum.requireOpenableSubjects(anySet())).thenAnswer(invocation -> {
            Set<Long> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(id -> SubjectSimpleResponse.builder()
                            .id(id)
                            .subjectCode("CS10" + id)
                            .subjectName("Học phần " + id)
                            .credits(3)
                            .build())
                    .toList();
        });
    }

    private Semester semester() {
        var semester = Semester.builder()
                .semesterCode("HK_1_2026_2027")
                .semesterName("Học kì 1 năm học 2026-2027")
                .startDate(LocalDate.parse("2026-08-11"))
                .endDate(LocalDate.parse("2027-01-05"))
                .registrationStart(LocalDate.parse("2026-08-01"))
                .registrationEnd(LocalDate.parse("2026-08-10"))
                .build();
        semester.setId(7L);
        return semester;
    }

    private ModuleClass historical(
            Semester semester, long subjectId, int sequence, ModuleClassStatus status, boolean deleted) {
        var subject = Subject.builder().build();
        subject.setId(subjectId);
        var lecturer = new Lecturer();
        lecturer.setId(5001L);
        var item = ModuleClass.builder()
                .semester(semester)
                .subject(subject)
                .lecturer(lecturer)
                .classSequence(sequence)
                .moduleClassCode("HISTORY_" + sequence)
                .maxCapacity(80)
                .status(status)
                .build();
        item.setDeleted(deleted);
        return item;
    }

    private CreateModuleClassesRequest batch(SubjectClassesRequest... subjects) {
        var request = new CreateModuleClassesRequest();
        request.setSubjects(List.of(subjects));
        return request;
    }

    private SubjectClassesRequest subjectRequest(long subjectId, int... capacities) {
        var request = new SubjectClassesRequest();
        request.setSubjectId(subjectId);
        request.setClasses(Arrays.stream(capacities)
                .mapToObj(capacity -> {
                    var item = new CreateModuleClassRequest();
                    item.setMaxCapacity(capacity);
                    return item;
                })
                .toList());
        return request;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                .isEqualTo(expected));
    }
}
