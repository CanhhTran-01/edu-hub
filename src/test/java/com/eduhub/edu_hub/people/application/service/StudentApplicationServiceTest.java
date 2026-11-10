package com.eduhub.edu_hub.people.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.StudentSearchRequest;
import com.eduhub.edu_hub.people.api.mapper.StudentResponseMapper;
import com.eduhub.edu_hub.people.application.exception.ProfileSearchException;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class StudentApplicationServiceTest {
    @Mock
    private StudentRepository students;

    @Mock
    private CurriculumFacade.ReferenceData curriculum;

    @Mock
    private IdentityFacade identityFacade;

    @Spy
    private StudentResponseMapper studentResponseMapper = new StudentResponseMapper();

    private StudentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new StudentApplicationService(
                students, curriculum, studentResponseMapper, new ProfileAccountEmailService(identityFacade));
    }

    @Test
    void profileGetsTheCurrentMajorNameThroughTheCurriculumFacade() {
        var student = new Student();
        var administrativeClass = new AdministrativeClass();
        var major = Major.builder().build();
        major.setId(2L);
        administrativeClass.setMajor(major);
        administrativeClass.setCohort(new Cohort());
        administrativeClass.setTrainingType(TrainingType.CHINH_QUY);
        student.setAdministrativeClass(administrativeClass);
        when(students.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(student));
        when(curriculum.findMajorsByIds(Set.of(2L)))
                .thenReturn(List.of(new MajorSimpleResponse(2L, "ATTT", "Tên ngành từ Curriculum", null)));

        assertThat(service.findById(1L).getMajorName()).isEqualTo("Tên ngành từ Curriculum");
    }

    @Test
    void listReadsAccountEmailsInOneBatchAndKeepsProfilesWithoutAccounts() {
        Student firstStudent = new Student();
        firstStudent.setId(1L);
        firstStudent.setUserId(101L);
        Student secondStudent = new Student();
        secondStudent.setId(2L);
        secondStudent.setUserId(102L);
        Student unlinkedStudent = new Student();
        unlinkedStudent.setId(3L);
        when(students.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstStudent, secondStudent, unlinkedStudent)));
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L, 102L)))
                .thenReturn(List.of(
                        new UserAccountEmailResponse(101L, "shared@example.com"),
                        new UserAccountEmailResponse(102L, "shared@example.com")));

        var response = service.search(new StudentSearchRequest());

        assertThat(response.getContent())
                .extracting(student -> student.getEmail())
                .containsExactly("shared@example.com", "shared@example.com", null);
        verify(identityFacade, times(1)).findAccountEmailsByUserIds(Set.of(101L, 102L));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1"})
    void rejectsInvalidPaginationBeforeQueryingAnyData(int page, int size) {
        StudentSearchRequest request = new StudentSearchRequest();
        request.setPage(page);
        request.setSize(size);

        assertThatThrownBy(() -> service.search(request))
                .isInstanceOfSatisfying(ProfileSearchException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(students, curriculum, identityFacade);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "studying", " STUDYING "})
    void rejectsInvalidActivityStatusBeforeQueryingAnyData(String activityStatus) {
        StudentSearchRequest request = new StudentSearchRequest();
        request.setActivityStatus(activityStatus);

        assertThatThrownBy(() -> service.search(request)).isInstanceOf(ProfileSearchException.class);
        verifyNoInteractions(students, curriculum, identityFacade);
    }

    @ParameterizedTest
    @CsvSource({"0,20", "2,5"})
    void usesRequestPaginationAndAscendingIdSort(int page, int size) {
        StudentSearchRequest request = new StudentSearchRequest();
        if (page != 0 || size != 20) {
            request.setPage(page);
            request.setSize(size);
        }
        when(students.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));

        var response = service.search(request);

        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(students).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(page);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(size);
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by("id").ascending());
        assertThat(response.getPageNumber()).isEqualTo(page);
        assertThat(response.getPageSize()).isEqualTo(size);
        assertThat(response.getContent()).isEmpty();
        verifyNoInteractions(curriculum, identityFacade);
    }

    @ParameterizedTest
    @EnumSource(ActivityStatus.class)
    void combinesActivityStatusWithKeywordAndAcademicScope(ActivityStatus activityStatus) {
        StudentSearchRequest request = new StudentSearchRequest();
        request.setKeyword(" An ");
        request.setClassId(3L);
        request.setMajorId(2L);
        request.setCohortId(8L);
        request.setActivityStatus(activityStatus.name());
        when(students.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search(request);

        ArgumentCaptor<Specification<Student>> filtersCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(students).findAll(filtersCaptor.capture(), any(Pageable.class));
        Root<Student> studentRoot = mock(Root.class, RETURNS_DEEP_STUBS);
        Join<Student, AdministrativeClass> administrativeClass = mock(Join.class, RETURNS_DEEP_STUBS);
        Join<AdministrativeClass, Cohort> cohort = mock(Join.class, RETURNS_DEEP_STUBS);
        when(studentRoot.<Student, AdministrativeClass>join("administrativeClass", JoinType.LEFT))
                .thenReturn(administrativeClass);
        when(administrativeClass.<AdministrativeClass, Cohort>join("cohort", JoinType.LEFT))
                .thenReturn(cohort);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        var studentCodeExpression = builder.lower(studentRoot.get("studentCode"));
        var fullNameExpression = builder.lower(studentRoot.get("fullName"));

        filtersCaptor.getValue().toPredicate(studentRoot, null, builder);

        verify(builder).isFalse(studentRoot.get("deleted"));
        verify(builder).equal(studentRoot.get("activityStatus"), activityStatus);
        verify(builder).like(studentCodeExpression, "% an %");
        verify(builder).like(fullNameExpression, "% an %");
        verify(builder).equal(administrativeClass.get("id"), 3L);
        verify(builder).equal(administrativeClass.get("major").get("id"), 2L);
        verify(builder).equal(cohort.get("id"), 8L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingOrBlankCriteriaOnlyExcludeDeletedStudents(String searchText) {
        StudentSearchRequest request = new StudentSearchRequest();
        request.setKeyword(searchText);
        request.setActivityStatus(searchText);
        when(students.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search(request);

        ArgumentCaptor<Specification<Student>> filtersCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(students).findAll(filtersCaptor.capture(), any(Pageable.class));
        Root<Student> studentRoot = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        filtersCaptor.getValue().toPredicate(studentRoot, null, builder);
        verify(builder).isFalse(studentRoot.get("deleted"));
        verifyNoMoreInteractions(builder);
    }

    @Test
    void detailReadsEmailFromLinkedUserEvenWhenNoOtherProfileFieldsChange() {
        Student student = new Student();
        student.setId(1L);
        student.setUserId(101L);
        when(students.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(student));
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L)))
                .thenReturn(List.of(new UserAccountEmailResponse(101L, "account@example.com")));

        assertThat(service.findById(1L).getEmail()).isEqualTo("account@example.com");
    }
}
