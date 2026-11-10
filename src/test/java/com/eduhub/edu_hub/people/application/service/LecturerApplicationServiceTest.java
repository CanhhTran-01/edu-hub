package com.eduhub.edu_hub.people.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.LecturerSearchRequest;
import com.eduhub.edu_hub.people.api.mapper.LecturerResponseMapper;
import com.eduhub.edu_hub.people.application.exception.ProfileSearchException;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Faculty;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.model.LecturerTeachingSubject;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerTeachingSubjectRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.databind.json.JsonMapper;

class LecturerApplicationServiceTest {
    private final LecturerRepository lecturerRepository = mock(LecturerRepository.class);
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final LecturerTeachingSubjectRepository teachingSubjectRepository =
            mock(LecturerTeachingSubjectRepository.class);
    private final CurriculumFacade.TeachingSubjects curriculumTeachingSubjects =
            mock(CurriculumFacade.TeachingSubjects.class);
    private final LecturerApplicationService lecturerApplicationService = new LecturerApplicationService(
            lecturerRepository,
            new ProfileAccountEmailService(identityFacade),
            teachingSubjectRepository,
            curriculumTeachingSubjects,
            new LecturerResponseMapper());

    @Test
    void searchGetsCurrentAccountEmailsInOneBatchAndKeepsUnlinkedProfiles() {
        Lecturer firstLecturer = lecturer(1L, 101L);
        Lecturer secondLecturer = lecturer(2L, 102L);
        Lecturer unlinkedLecturer = lecturer(3L, null);
        when(lecturerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstLecturer, secondLecturer, unlinkedLecturer)));
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L, 102L)))
                .thenReturn(List.of(
                        new UserAccountEmailResponse(101L, "first@example.com"),
                        new UserAccountEmailResponse(102L, null)));

        var response = lecturerApplicationService.search(new LecturerSearchRequest());

        assertThat(response.getContent())
                .extracting(lecturer -> lecturer.getEmail())
                .containsExactly("first@example.com", null, null);
        verify(identityFacade, times(1)).findAccountEmailsByUserIds(Set.of(101L, 102L));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1"})
    void rejectsInvalidPaginationBeforeQueryingAnyData(int page, int size) {
        LecturerSearchRequest request = new LecturerSearchRequest();
        request.setPage(page);
        request.setSize(size);

        assertThatThrownBy(() -> lecturerApplicationService.search(request))
                .isInstanceOfSatisfying(ProfileSearchException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(lecturerRepository, identityFacade);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDYING", "active", "UNKNOWN"})
    void rejectsInvalidStatusBeforeQueryingAnyData(String status) {
        LecturerSearchRequest request = new LecturerSearchRequest();
        request.setStatus(status);

        assertThatThrownBy(() -> lecturerApplicationService.search(request)).isInstanceOf(ProfileSearchException.class);
        verifyNoInteractions(lecturerRepository, identityFacade);
    }

    @ParameterizedTest
    @CsvSource({"0,20", "2,5"})
    void usesRequestPaginationAndAscendingIdSort(int page, int size) {
        LecturerSearchRequest request = new LecturerSearchRequest();
        if (page != 0 || size != 20) {
            request.setPage(page);
            request.setSize(size);
        }
        when(lecturerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));

        var response = lecturerApplicationService.search(request);

        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(lecturerRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(page);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(size);
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by("id").ascending());
        assertThat(response.getPageNumber()).isEqualTo(page);
        assertThat(response.getPageSize()).isEqualTo(size);
        assertThat(response.getContent()).isEmpty();
        verifyNoInteractions(identityFacade);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "INACTIVE", " INACTIVE "})
    void combinesLecturerStatusWithOtherCriteriaAndExcludesDeletedFaculty(String status) {
        LecturerSearchRequest request = new LecturerSearchRequest();
        request.setKeyword(" An ");
        request.setLecturerCode(" GV ");
        request.setFullName(" Nguyen ");
        request.setFacultyId(2L);
        request.setStatus(status);
        when(lecturerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        lecturerApplicationService.search(request);

        ArgumentCaptor<Specification<Lecturer>> filtersCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(lecturerRepository).findAll(filtersCaptor.capture(), any(Pageable.class));
        Root<Lecturer> lecturerRoot = mock(Root.class, RETURNS_DEEP_STUBS);
        Join<Lecturer, Faculty> faculty = mock(Join.class, RETURNS_DEEP_STUBS);
        when(lecturerRoot.<Lecturer, Faculty>join("faculty", JoinType.INNER)).thenReturn(faculty);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        var lecturerCodeExpression = builder.lower(lecturerRoot.get("lecturerCode"));
        var fullNameExpression = builder.lower(lecturerRoot.get("fullName"));

        filtersCaptor.getValue().toPredicate(lecturerRoot, null, builder);

        verify(builder).isFalse(lecturerRoot.get("deleted"));
        verify(builder).equal(lecturerRoot.get("status"), LecturerStatus.valueOf(status.trim()));
        verify(builder).like(lecturerCodeExpression, "%an%");
        verify(builder).like(fullNameExpression, "%an%");
        verify(builder).like(lecturerCodeExpression, "%gv%");
        verify(builder).like(fullNameExpression, "%nguyen%");
        verify(builder).equal(faculty.get("id"), 2L);
        verify(builder).isFalse(faculty.get("deleted"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingOrBlankCriteriaOnlyExcludeDeletedLecturers(String searchText) {
        LecturerSearchRequest request = new LecturerSearchRequest();
        request.setKeyword(searchText);
        request.setLecturerCode(searchText);
        request.setFullName(searchText);
        request.setStatus(searchText);
        when(lecturerRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        lecturerApplicationService.search(request);

        ArgumentCaptor<Specification<Lecturer>> filtersCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(lecturerRepository).findAll(filtersCaptor.capture(), any(Pageable.class));
        Root<Lecturer> lecturerRoot = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder builder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        filtersCaptor.getValue().toPredicate(lecturerRoot, null, builder);
        verify(builder).isFalse(lecturerRoot.get("deleted"));
        verifyNoMoreInteractions(builder);
    }

    @Test
    void detailIncludesEmailFromUserWithoutRequiringAnActiveLecturer() {
        Lecturer lecturer = lecturer(1L, 101L);
        lecturer.setStatus(LecturerStatus.INACTIVE);
        when(lecturerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(lecturer));
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L)))
                .thenReturn(List.of(new UserAccountEmailResponse(101L, "current@example.com")));

        var response = lecturerApplicationService.findById(1L);

        assertThat(response.getEmail()).isEqualTo("current@example.com");
        assertThat(response.getStatus()).isEqualTo(LecturerStatus.INACTIVE);
        var profileJson = JsonMapper.builder().build().valueToTree(response);
        assertThat(profileJson.has("accountNotificationStatus")).isFalse();
        assertThat(profileJson.has("accountNotificationError")).isFalse();
    }

    @Test
    void schedulingLookupReadsEmailsForTheWholeSetWithoutDroppingInactiveLecturers() {
        Lecturer firstLecturer = lecturer(1L, 101L);
        Lecturer secondLecturer = lecturer(2L, 102L);
        secondLecturer.setStatus(LecturerStatus.INACTIVE);
        when(lecturerRepository.findAllByIdInAndDeletedFalse(Set.of(1L, 2L)))
                .thenReturn(List.of(firstLecturer, secondLecturer));
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L, 102L)))
                .thenReturn(List.of(
                        new UserAccountEmailResponse(101L, "first@example.com"),
                        new UserAccountEmailResponse(102L, "second@example.com")));

        var response = lecturerApplicationService.findByIds(Set.of(1L, 2L));

        assertThat(response).hasSize(2);
        assertThat(response.getLast().getStatus()).isEqualTo(LecturerStatus.INACTIVE);
        assertThat(response.getLast().getEmail()).isEqualTo("second@example.com");
        verify(identityFacade, times(1)).findAccountEmailsByUserIds(Set.of(101L, 102L));
    }

    @Test
    void emptyLookupDoesNotCallIdentityOrTheRepository() {
        assertThat(lecturerApplicationService.findByIds(Set.of())).isEmpty();
        verifyNoInteractions(identityFacade, lecturerRepository);
    }

    @Test
    void loadsTeachingSubjectsInBatchesAndPreservesLegacyCodesAndUnknownRecruitmentYear() {
        Lecturer firstLecturer = lecturer(1L, 101L);
        firstLecturer.setLecturerCode("GV000001");
        Lecturer secondLecturer = lecturer(2L, null);
        secondLecturer.setLecturerCode("CNTT26001");
        secondLecturer.setRecruitmentYear(2026);
        when(lecturerRepository.findAllByIdInAndDeletedFalse(Set.of(1L, 2L)))
                .thenReturn(List.of(firstLecturer, secondLecturer));
        when(teachingSubjectRepository.findByLecturerIdInAndDeletedFalseOrderByLecturerIdAscSubjectIdAsc(
                        Set.of(1L, 2L)))
                .thenReturn(List.of(LecturerTeachingSubject.builder()
                        .lecturerId(2L)
                        .subjectId(10L)
                        .build()));
        when(curriculumTeachingSubjects.findTeachingSubjectsByIds(Set.of(10L)))
                .thenReturn(List.of(SubjectSimpleResponse.builder()
                        .id(10L)
                        .subjectName("Môn lịch sử")
                        .build()));
        var lecturerResponses = lecturerApplicationService.findByIds(Set.of(1L, 2L));
        assertThat(lecturerResponses.getFirst().getLecturerCode()).isEqualTo("GV000001");
        assertThat(lecturerResponses.getFirst().getRecruitmentYear()).isNull();
        assertThat(lecturerResponses.getFirst().getTeachingSubjects()).isEmpty();
        assertThat(lecturerResponses.getLast().getTeachingSubjects())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(10L);
        verify(curriculumTeachingSubjects, times(1)).findTeachingSubjectsByIds(Set.of(10L));
    }

    private Lecturer lecturer(Long lecturerId, Long userId) {
        Lecturer lecturer = new Lecturer();
        lecturer.setId(lecturerId);
        lecturer.setUserId(userId);
        lecturer.setStatus(LecturerStatus.ACTIVE);
        return lecturer;
    }
}
