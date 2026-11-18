package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.CurriculumOpeningSubjectResponse;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateModuleClassesRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.SubjectClassesRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassOpeningException;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.AcademicPlanningSnapshot;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.policy.OpeningDemandPolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ModuleClassApplicationServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-02T10:00:00Z"),
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
                roomRepository);
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

        var result = service.createModuleClasses(7L, request);

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
        verifyNoInteractions(people, academicPlanningSnapshotService, moduleClassEnrollmentQueryService);
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
        var existing = historical(semester, 4, ModuleClassStatus.UNSCHEDULED, true);
        when(classes.findAllBySemester_IdOrderByIdAsc(7L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createModuleClasses(7L, batch(subjectRequest(2, 80), subjectRequest(1, 80))))
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
                        historical(semester, 2, ModuleClassStatus.OPEN, false),
                        historical(semester, 9, ModuleClassStatus.CANCELLED, true)));
        when(classes.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = subjectRequest(1, 80);
        request.setConfirmAdditionalClasses(true);

        assertThat(service.createModuleClasses(7L, batch(request)))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getClassSequence()).isEqualTo(10);
                    assertThat(item.getModuleClassCode()).isEqualTo("MC_7_CS101_010");
                });
    }

    @Test
    void onlyCancelledClassesNeedNoConfirmationButDoReserveTheirSequence() {
        var semester = semester();
        prepare(semester);
        when(classes.findAllBySemester_IdOrderByIdAsc(7L))
                .thenReturn(List.of(historical(semester, 3, ModuleClassStatus.CANCELLED, false)));
        when(classes.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.createModuleClasses(7L, batch(subjectRequest(1, 80))))
                .singleElement()
                .satisfies(item -> assertThat(item.getClassSequence()).isEqualTo(4));
    }

    @Test
    void duplicateSubjectsAreRejectedRatherThanSilentlyMerged() {
        assertError(
                () -> service.createModuleClasses(7L, batch(subjectRequest(1, 80), subjectRequest(1, 80))),
                ErrorCode.DUPLICATE_BATCH_SUBJECT);
        verifyNoInteractions(semesters, classes, curriculum, academicPlanningSnapshotService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 81})
    void capacityOutsideOneToEightyIsRejected(int capacity) {
        var subject = subjectRequest(1, 80);
        subject.getClasses().getFirst().setMaxCapacity(capacity);
        assertError(() -> service.createModuleClasses(7L, batch(subject)), ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(semesters, classes, curriculum, academicPlanningSnapshotService);
    }

    @Test
    void lockedSemesterRejectsClassOpeningBeforeCallingOtherModules() {
        var semester = semester();
        semester.setLocked(true);
        when(semesters.findForUpdateByIdAndDeletedFalse(7L)).thenReturn(Optional.of(semester));
        assertError(() -> service.createModuleClasses(7L, batch(subjectRequest(1, 80))), ErrorCode.SEMESTER_LOCKED);
        verifyNoInteractions(classes, curriculum, academicPlanningSnapshotService);
    }

    @Test
    void semesterEndsOnlyAfterItsEndDateInVietnam() {
        var semester = semester();
        semester.setEndDate(LocalDate.parse("2026-11-01"));
        when(semesters.findForUpdateByIdAndDeletedFalse(7L)).thenReturn(Optional.of(semester));
        assertError(() -> service.createModuleClasses(7L, batch(subjectRequest(1, 80))), ErrorCode.SEMESTER_ENDED);
        verifyNoInteractions(classes, curriculum, academicPlanningSnapshotService);
    }

    @Test
    void missingSemesterAndClassReturnBusinessNotFound() {
        assertError(() -> service.createModuleClasses(7L, batch(subjectRequest(1, 80))), ErrorCode.SEMESTER_NOT_FOUND);
        assertError(() -> service.findModuleClassById(99L), ErrorCode.MODULE_CLASS_NOT_FOUND);
    }

    @Test
    void openingSuggestionsKeepDemandMetadataAndCountHiddenNonCancelledClasses() {
        var targetSemester = semester();
        when(semesters.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(targetSemester));
        when(academicPlanningSnapshotService.loadPlanningSnapshot())
                .thenReturn(new AcademicPlanningSnapshot(
                        "DATABASE",
                        true,
                        List.of(new AcademicPlanningSnapshot.PlanningStudent(
                                1L, "SV01", "CTDT01", 2026, ActivityStatus.STUDYING)),
                        List.of()));
        when(curriculum.findPlanningSubjects())
                .thenReturn(List.of(CurriculumOpeningSubjectResponse.builder()
                        .curriculumCode("CTDT01")
                        .subjectId(1L)
                        .subjectCode("CS101")
                        .subjectName("Nhập môn lập trình")
                        .credits(3)
                        .recommendSemester(1)
                        .build()));
        when(classes.findAllBySemester_IdOrderByIdAsc(7L))
                .thenReturn(List.of(
                        historical(targetSemester, 1, ModuleClassStatus.UNSCHEDULED, false),
                        historical(targetSemester, 2, ModuleClassStatus.SCHEDULED, true),
                        historical(targetSemester, 3, ModuleClassStatus.CANCELLED, false)));

        assertThat(service.findSubjectsToOpen(7L)).singleElement().satisfies(openingSubject -> {
            assertThat(openingSubject.getSubjectId()).isEqualTo(1L);
            assertThat(openingSubject.getSubjectCode()).isEqualTo("CS101");
            assertThat(openingSubject.getSubjectName()).isEqualTo("Nhập môn lập trình");
            assertThat(openingSubject.getCredits()).isEqualTo(3);
            assertThat(openingSubject.getExpectedStudentCount()).isEqualTo(1);
            assertThat(openingSubject.getOnTimeStudentCount()).isEqualTo(1);
            assertThat(openingSubject.getRecommendedClassCount()).isEqualTo(1);
            assertThat(openingSubject.getExistingClassCount()).isEqualTo(2);
            assertThat(openingSubject.getDataSource()).isEqualTo("DATABASE");
            assertThat(openingSubject.isHistoryComplete()).isTrue();
        });
        verifyNoInteractions(people, moduleClassEnrollmentQueryService, entityManager);
        verify(classes, never()).saveAllAndFlush(anyList());
    }

    @Test
    void openingSuggestionsDoNotTreatMissingHistoryAsEmptyDemand() {
        when(semesters.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(semester()));
        when(academicPlanningSnapshotService.loadPlanningSnapshot())
                .thenReturn(new AcademicPlanningSnapshot("DATABASE", false, List.of(), List.of()));
        when(curriculum.findPlanningSubjects()).thenReturn(List.of());

        assertError(() -> service.findSubjectsToOpen(7L), ErrorCode.PLANNING_DATA_UNAVAILABLE);

        verifyNoInteractions(classes, people, moduleClassEnrollmentQueryService, entityManager);
    }

    @Test
    void searchBuildsSimpleRowsWithoutLoadingClassSchedules() {
        var targetSemester = semester();
        var scheduledClass = historical(targetSemester, 1, ModuleClassStatus.SCHEDULED, false);
        scheduledClass.setId(101L);
        var unscheduledClass = ModuleClass.builder()
                .semester(targetSemester)
                .subject(scheduledClass.getSubject())
                .moduleClassCode("MC_7_CS101_002")
                .maxCapacity(40)
                .currentSize(0)
                .status(ModuleClassStatus.UNSCHEDULED)
                .build();
        unscheduledClass.setId(102L);
        var pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "id"));
        when(classes.findAll(ArgumentMatchers.<Specification<ModuleClass>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(scheduledClass, unscheduledClass), pageable, 5));
        when(curriculum.findSubjectsByIds(Set.of(1L)))
                .thenReturn(List.of(SubjectSimpleResponse.builder()
                        .id(1L)
                        .subjectCode("CS101")
                        .subjectName("Nhập môn lập trình")
                        .credits(3)
                        .build()));
        when(people.findLecturersByIds(Set.of(5001L)))
                .thenReturn(List.of(LecturerResponse.builder()
                        .id(5001L)
                        .fullName("Giảng viên A")
                        .build()));
        var request = new ModuleClassSearchRequest();
        request.setPage(1);
        request.setSize(2);
        var response = service.searchModuleClasses(request);
        assertThat(response.getPageNumber()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(5);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().getFirst().getSubjectName()).isEqualTo("Nhập môn lập trình");
        assertThat(response.getContent().getFirst().getLecturerName()).isEqualTo("Giảng viên A");
        assertThat(response.getContent().getFirst().getSemesterCode()).isEqualTo("HK_1_2026_2027");
        assertThat(response.getContent().get(1).getLecturerName()).isNull();
        verify(classes).findAll(ArgumentMatchers.<Specification<ModuleClass>>any(), eq(pageable));
        verifyNoInteractions(classScheduleQueryService, classScheduleRepository, roomRepository);
    }

    @Test
    void emptySearchKeepsTotalCountWithoutLoadingDisplayData() {
        var pageable = PageRequest.of(2, 2, Sort.by(Sort.Direction.DESC, "id"));
        when(classes.findAll(ArgumentMatchers.<Specification<ModuleClass>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 3));
        var request = new ModuleClassSearchRequest();
        request.setPage(2);
        request.setSize(2);
        var response = service.searchModuleClasses(request);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getPageNumber()).isEqualTo(2);
        verifyNoInteractions(curriculum, people, classScheduleQueryService);
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

    private ModuleClass historical(Semester semester, int sequence, ModuleClassStatus status, boolean deleted) {
        var subject = Subject.builder().build();
        subject.setId((long) 1);
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
