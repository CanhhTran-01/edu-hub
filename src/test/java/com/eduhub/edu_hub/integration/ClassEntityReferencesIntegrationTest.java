package com.eduhub.edu_hub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.people.api.dto.request.StudentSearchRequest;
import com.eduhub.edu_hub.people.application.StudentApplicationService;
import com.eduhub.edu_hub.people.application.code.StudentCodeGenerator;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.AdministrativeClassRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateModuleClassesRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.SubjectClassesRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.application.ModuleClassApplicationService;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import com.eduhub.edu_hub.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ClassEntityReferencesIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private AdministrativeClassRepository administrativeClasses;

    @Autowired
    private StudentRepository students;

    @Autowired
    private LecturerRepository lecturers;

    @Autowired
    private SemesterRepository semesters;

    @Autowired
    private ModuleClassRepository moduleClasses;

    @Autowired
    private StudentApplicationService studentService;

    @Autowired
    private ModuleClassApplicationService moduleClassService;

    @Autowired
    private StudentCodeGenerator studentCodes;

    @Autowired
    private PeopleFacade.Scheduling people;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migratesSeededAdministrativeClassesAndQueriesByMajorIdAndCode() {
        var byCode =
                administrativeClasses
                        .findByMajor_MajorCodeIgnoreCaseAndCohort_CohortCodeIgnoreCaseAndTrainingTypeAndDeletedFalse(
                                "cntt", "ct8", TrainingType.CHINH_QUY);
        assertThat(byCode).hasSize(2);
        var major = byCode.getFirst().getMajor();
        assertThat(major.getMajorCode()).isEqualTo("CNTT");
        assertThat(administrativeClasses.findByMajor_IdAndCohort_CohortCodeIgnoreCaseAndTrainingTypeAndDeletedFalse(
                        major.getId(), "CT8", TrainingType.CHINH_QUY))
                .extracting(BaseEntity::getId)
                .containsExactlyInAnyOrderElementsOf(
                        byCode.stream().map(BaseEntity::getId).toList());
        assertThat(studentCodes.generate(
                        byCode.stream()
                                .filter(value -> value.getClassName().equals("L01"))
                                .findFirst()
                                .orElseThrow(),
                        1))
                .isEqualTo("CNTT080101");
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.columns where table_schema = current_schema() "
                                + "and table_name = 'administrative_classes' and column_name = 'major_code'",
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select indexdef from pg_indexes where schemaname = current_schema() "
                                + "and indexname = 'idx_administrative_classes_admission_scope'",
                        String.class))
                .contains("(major_id, cohort_id, training_type)");
    }

    @Test
    void studentSearchAndPlanningKeepUsingTheRelatedMajorId() {
        var administrativeClass = administrativeClasses
                .findByMajor_MajorCodeIgnoreCaseAndCohort_CohortCodeIgnoreCaseAndTrainingTypeAndDeletedFalse(
                        "CNTT", "CT8", TrainingType.CHINH_QUY)
                .getFirst();
        var cohort = administrativeClass.getCohort();
        cohort.setStartYear(2026);
        cohort.setEndYear(2030);
        var student = new Student();
        student.setStudentCode("CT080101");
        student.setFullName("Sinh viên kiểm tra quan hệ ngành");
        student.setAdministrativeClass(administrativeClass);
        student.setActivityStatus(ActivityStatus.STUDYING);
        students.saveAndFlush(student);
        Long majorId = administrativeClass.getMajor().getId();
        Long studentId = student.getId();
        entityManager.clear();

        var request = new StudentSearchRequest();
        request.setMajorId(majorId);
        request.setCohortId(cohort.getId());
        assertThat(studentService.search(request, 0, 20).getContent())
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.getId()).isEqualTo(studentId);
                    assertThat(value.getMajorName()).isEqualTo("Công nghệ thông tin");
                });
        assertThat(people.findStudentsForPlanning()).singleElement().satisfies(value -> {
            assertThat(value.majorId()).isEqualTo(majorId);
            assertThat(value.admissionYear()).isEqualTo(2026);
        });
    }

    @Test
    void batchCreationPersistsSubjectReferencesAndLeavesLecturerUnassigned() {
        var semester = futureSemester();
        var subject = subject();
        var capacity = new CreateModuleClassRequest();
        capacity.setMaxCapacity(50);
        var subjectRequest = new SubjectClassesRequest();
        subjectRequest.setSubjectId(subject.getId());
        subjectRequest.setClasses(List.of(capacity));
        var request = new CreateModuleClassesRequest();
        request.setSubjects(List.of(subjectRequest));

        var response = moduleClassService.createBatch(semester.getId(), request).getFirst();
        entityManager.clear();

        var saved = moduleClasses.findByIdAndDeletedFalse(response.getId()).orElseThrow();
        assertThat(saved.getSubject().getId()).isEqualTo(subject.getId());
        assertThat(saved.getLecturer()).isNull();
        assertThat(response.getSubjectId()).isEqualTo(subject.getId());
        assertThat(response.getLecturerId()).isNull();
        assertThat(response.getStatus()).isEqualTo(ModuleClassStatus.UNSCHEDULED);
    }

    @Test
    void moduleClassFiltersUseRelationsAndRetainUnassignedAndHistoricalClasses() {
        var semester = futureSemester();
        var subject = subject();
        var lecturer = new Lecturer();
        lecturer.setLecturerCode("TEST-RELATION-GV");
        lecturer.setFullName("Giảng viên đã ngừng hoạt động");
        lecturer.setStatus(LecturerStatus.INACTIVE);
        lecturers.saveAndFlush(lecturer);
        var assigned = moduleClasses.saveAndFlush(moduleClass(semester, subject, lecturer, 1));
        var unassigned = moduleClasses.saveAndFlush(moduleClass(semester, subject, null, 2));
        subject.setDeleted(true);
        entityManager.flush();
        entityManager.clear();

        var request = new ModuleClassSearchRequest();
        request.setSemesterId(semester.getId());
        request.setSubjectId(subject.getId());
        assertThat(moduleClassService.search(request).getContent())
                .extracting(ModuleClassResponse::getId)
                .containsExactly(unassigned.getId(), assigned.getId());
        request.setLecturerId(lecturer.getId());
        assertThat(moduleClassService.search(request).getContent())
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.getId()).isEqualTo(assigned.getId());
                    assertThat(value.getLecturerId()).isEqualTo(lecturer.getId());
                    assertThat(value.getLecturerName()).isEqualTo(lecturer.getFullName());
                    assertThat(value.getSubjectName()).isEqualTo(subject.getSubjectName());
                });
    }

    private Semester futureSemester() {
        int year = Math.max(LocalDate.now().getYear() + 10, 2040);
        return semesters.saveAndFlush(Semester.builder()
                .semesterCode("HK_1_" + year + "_" + (year + 1))
                .semesterName("Học kì 1 năm học " + year + "-" + (year + 1))
                .startDate(LocalDate.of(year, 8, 11))
                .endDate(LocalDate.of(year + 1, 1, 5))
                .registrationStart(LocalDate.of(year, 8, 1))
                .registrationEnd(LocalDate.of(year, 8, 10))
                .build());
    }

    private Subject subject() {
        return entityManager
                .createQuery("select s from Subject s where s.deleted = false order by s.id", Subject.class)
                .setMaxResults(1)
                .getSingleResult();
    }

    private ModuleClass moduleClass(Semester semester, Subject subject, Lecturer lecturer, int sequence) {
        return ModuleClass.builder()
                .semester(semester)
                .subject(subject)
                .lecturer(lecturer)
                .moduleClassCode("TEST-RELATION-MC-" + sequence)
                .classSequence(sequence)
                .maxCapacity(50)
                .status(ModuleClassStatus.UNSCHEDULED)
                .build();
    }
}
