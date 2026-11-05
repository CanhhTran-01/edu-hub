package com.eduhub.edu_hub.curriculum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.curriculum.api.dto.request.CreateSubjectRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.SubjectActionRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.SubjectSearchRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.UpdateSubjectRequest;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectResponse;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.exception.SubjectHasDependentsException;
import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.curriculum.domain.model.SubjectPrerequisite;
import com.eduhub.edu_hub.curriculum.domain.repository.SubjectPrerequisiteRepository;
import com.eduhub.edu_hub.curriculum.domain.repository.SubjectRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class SubjectApplicationServiceTest {
    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SubjectPrerequisiteRepository subjectPrerequisiteRepository;

    @InjectMocks
    private SubjectApplicationService service;

    @Test
    void savesSubjectAndReturnsResult() {
        CreateSubjectRequest request = validRequest();
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject subject = invocation.getArgument(0);
            subject.setId(14L);
            return subject;
        });

        SubjectResponse response = service.createSubject(request);

        assertThat(response.getId()).isEqualTo(14L);
        assertThat(response.getSubjectCode()).isEqualTo("CS101");
        assertThat(response.getSubjectName()).isEqualTo("Lập trình cơ bản");
        assertThat(response.getCredits()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo(SubjectStatus.ACTIVE);

        var captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo(request.getDescription());
        assertThat(captor.getValue().isDeleted()).isFalse();
    }

    @Test
    void rejectsDuplicateCode() {
        when(subjectRepository.existsBySubjectCode("CS101")).thenReturn(true);

        assertThatThrownBy(() -> service.createSubject(validRequest()))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_SUBJECT_CODE));

        verify(subjectRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateName() {
        when(subjectRepository.existsBySubjectName("Lập trình cơ bản")).thenReturn(true);

        assertThatThrownBy(() -> service.createSubject(validRequest()))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_SUBJECT_NAME));

        verify(subjectRepository, never()).save(any());
    }

    @Test
    void returnsPaginatedResultsFromSpecificationSearch() {
        var request = new SubjectSearchRequest();
        request.setKeyword(" CS ");
        request.setSubjectCode("CS");
        request.setSubjectName("trình");
        request.setCredits(3);
        request.setStatus(SubjectStatus.ACTIVE);
        request.setWithoutPrerequisites(true);
        request.setPage(1);
        request.setSize(1);
        var pageable = PageRequest.of(1, 1, Sort.by("id"));
        Subject subject = subject(14L, "CS101", "Lập trình cơ bản");
        when(subjectRepository.findAll(ArgumentMatchers.<Specification<Subject>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(subject), pageable, 3));

        var response = service.searchSubjects(request);

        assertThat(response.getPageNumber()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(1);
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.isLast()).isFalse();
        assertThat(response.getContent()).singleElement().satisfies(result -> {
            assertThat(result.getSubjectCode()).isEqualTo("CS101");
            assertThat(result.getPrerequisites()).isEmpty();
        });
    }

    @Test
    void loadsRelationshipsForTheWholePageAndGroupsThemBySubject() {
        Subject a = subject(14L, "CS102", "Lập trình nâng cao");
        Subject b = subject(20L, "CS201", "Cấu trúc dữ liệu");
        Subject basic = subject(1L, "CS101", "Lập trình cơ bản");
        SubjectPrerequisite bRequiresA = link(b, a);
        var pageable = PageRequest.of(0, 20, Sort.by("id"));
        when(subjectRepository.findAll(ArgumentMatchers.<Specification<Subject>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(a, b), pageable, 2));
        when(subjectPrerequisiteRepository.findAllBySubject_IdInAndDeletedFalse(List.of(14L, 20L)))
                .thenReturn(List.of(bRequiresA, link(a, basic), link(b, basic)));
        when(subjectPrerequisiteRepository.findAllByPrerequisiteSubject_IdInAndDeletedFalse(List.of(14L, 20L)))
                .thenReturn(List.of(bRequiresA));

        var response = service.searchSubjects(new SubjectSearchRequest());

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().getFirst().getPrerequisites())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(1L);
        assertThat(response.getContent().getFirst().getRequiredBy())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(20L);
        assertThat(response.getContent().getLast().getPrerequisites())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(1L, 14L);
        assertThat(response.getContent().getLast().getRequiredBy()).isEmpty();
        verify(subjectPrerequisiteRepository).findAllBySubject_IdInAndDeletedFalse(List.of(14L, 20L));
        verify(subjectPrerequisiteRepository).findAllByPrerequisiteSubject_IdInAndDeletedFalse(List.of(14L, 20L));
        verifyNoMoreInteractions(subjectPrerequisiteRepository);
    }

    @Test
    void returnsEmptyPageWhenNothingMatches() {
        var request = new SubjectSearchRequest();
        var pageable = PageRequest.of(0, 20, Sort.by("id"));
        when(subjectRepository.findAll(ArgumentMatchers.<Specification<Subject>>any(), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        var response = service.searchSubjects(request);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        verifyNoInteractions(subjectPrerequisiteRepository);
    }

    @Test
    void returnsDetailWithDirectPrerequisitesAndDependentSubjects() {
        Subject subject = subject(14L, "CS102", "Lập trình nâng cao");
        when(subjectPrerequisiteRepository.findAllBySubject_IdInAndDeletedFalse(List.of(14L)))
                .thenReturn(List.of(link(subject, subject(1L, "CS101", "Lập trình cơ bản"))));
        when(subjectPrerequisiteRepository.findAllByPrerequisiteSubject_IdInAndDeletedFalse(List.of(14L)))
                .thenReturn(List.of(link(subject(20L, "CS201", "Cấu trúc dữ liệu"), subject)));
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));

        SubjectResponse response = service.getSubjectById(14L);

        assertThat(response.getId()).isEqualTo(14L);
        assertThat(response.getDescription()).isEqualTo("Mô tả học phần");
        assertThat(response.getPrerequisites()).singleElement().satisfies(prerequisite -> {
            assertThat(prerequisite.getId()).isEqualTo(1L);
            assertThat(prerequisite.getSubjectCode()).isEqualTo("CS101");
        });
        assertThat(response.getRequiredBy()).singleElement().satisfies(dependent -> {
            assertThat(dependent.getId()).isEqualTo(20L);
            assertThat(dependent.getSubjectCode()).isEqualTo("CS201");
        });
    }

    @Test
    void rejectsMissingOrDeletedSubject() {
        when(subjectRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubjectById(99L))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SUBJECT_NOT_FOUND));
    }

    @Test
    void excludesDeletedSubjectsAndDeletedLinksFromBothRelationshipLists() {
        Subject subject = subject(14L, "CS102", "Lập trình nâng cao");
        Subject deletedSubject = subject(1L, "CS101", "Học phần đã xóa");
        deletedSubject.setDeleted(true);

        Subject activeSubject = subject(20L, "CS201", "Học phần đang sử dụng");
        SubjectPrerequisite deletedPrerequisite = link(subject, activeSubject);
        deletedPrerequisite.setDeleted(true);
        SubjectPrerequisite deletedDependent = link(activeSubject, subject);
        deletedDependent.setDeleted(true);
        when(subjectPrerequisiteRepository.findAllBySubject_IdInAndDeletedFalse(List.of(14L)))
                .thenReturn(List.of(link(subject, deletedSubject), deletedPrerequisite));
        when(subjectPrerequisiteRepository.findAllByPrerequisiteSubject_IdInAndDeletedFalse(List.of(14L)))
                .thenReturn(List.of(link(deletedSubject, subject), deletedDependent));
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));

        SubjectResponse response = service.getSubjectById(14L);

        assertThat(response.getPrerequisites()).isEmpty();
        assertThat(response.getRequiredBy()).isEmpty();
    }

    @Test
    void updatesExistingSubjectWithoutReplacingItsIdentityOrRelationships() {
        Subject subject = subject(14L, "CS101", "Lập trình cơ bản");
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        subject.setCreatedAt(createdAt);
        when(subjectPrerequisiteRepository.findAllBySubject_IdInAndDeletedFalse(List.of(14L)))
                .thenReturn(List.of(link(subject, subject(1L, "CS100", "Nhập môn"))));
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));
        when(subjectRepository.save(subject)).thenReturn(subject);
        UpdateSubjectRequest request = updateRequest();
        request.setSubjectCode("CS101");

        SubjectResponse response = service.updateSubject(14L, request);

        assertThat(response.getId()).isEqualTo(14L);
        assertThat(response.getSubjectCode()).isEqualTo("CS101");
        assertThat(response.getSubjectName()).isEqualTo(request.getSubjectName());
        assertThat(response.getCredits()).isEqualTo(4);
        assertThat(response.getDescription()).isEqualTo(request.getDescription());
        assertThat(response.getStatus()).isEqualTo(SubjectStatus.ACTIVE);
        assertThat(response.getPrerequisites())
                .singleElement()
                .satisfies(prerequisite -> assertThat(prerequisite.getId()).isEqualTo(1L));
        assertThat(subject.getCreatedAt()).isEqualTo(createdAt);
        assertThat(subject.getUpdatedAt()).isNotNull();
        verify(subjectRepository).save(subject);
        verify(subjectPrerequisiteRepository, never()).saveAll(any());
    }

    @Test
    void allowsKeepingTheCurrentSubjectName() {
        Subject subject = subject(14L, "CS101", "Lập trình cơ bản");
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));
        when(subjectRepository.save(subject)).thenReturn(subject);
        UpdateSubjectRequest request = updateRequest();
        request.setSubjectName(subject.getSubjectName());

        SubjectResponse response = service.updateSubject(14L, request);

        assertThat(response.getSubjectName()).isEqualTo("Lập trình cơ bản");
        verify(subjectRepository).existsBySubjectNameAndIdNot("Lập trình cơ bản", 14L);
    }

    @Test
    void rejectsUpdatingMissingOrDeletedSubject() {
        when(subjectRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSubject(99L, updateRequest()))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SUBJECT_NOT_FOUND));

        verify(subjectRepository, never()).save(any());
    }

    @Test
    void rejectsUpdatingInactiveSubject() {
        Subject subject = Subject.builder()
                .subjectCode("CS101")
                .subjectName("Lập trình cơ bản")
                .credits(3)
                .status(SubjectStatus.INACTIVE)
                .build();
        subject.setId(14L);
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));

        assertThatThrownBy(() -> service.updateSubject(14L, updateRequest()))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SUBJECT_NOT_ACTIVE));

        verify(subjectRepository, never()).save(any());
    }

    @Test
    void rejectsChangingSubjectCode() {
        Subject subject = subject(14L, "CS101", "Lập trình cơ bản");
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));
        UpdateSubjectRequest request = updateRequest();
        request.setSubjectCode("CS999");

        assertThatThrownBy(() -> service.updateSubject(14L, request))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SUBJECT_CODE_IMMUTABLE));

        assertThat(subject.getSubjectCode()).isEqualTo("CS101");
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void rejectsUpdatingNameToAnotherSubjectsName() {
        Subject subject = subject(14L, "CS101", "Lập trình cơ bản");
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));
        when(subjectRepository.existsBySubjectNameAndIdNot("Lập trình nâng cao", 14L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.updateSubject(14L, updateRequest()))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_SUBJECT_NAME));

        assertThat(subject.getSubjectName()).isEqualTo("Lập trình cơ bản");
        assertThat(subject.getCredits()).isEqualTo(3);
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void deactivatesSubjectAndPreservesItsDataAndPrerequisites() {
        Subject subject = subject(14L, "CS101", "Lập trình cơ bản");
        when(subjectPrerequisiteRepository.findAllBySubject_IdInAndDeletedFalse(List.of(14L)))
                .thenReturn(List.of(link(subject, subject(1L, "CS100", "Nhập môn"))));
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));
        when(subjectRepository.save(subject)).thenReturn(subject);

        SubjectResponse response = service.deactivateSubject(14L, actionRequest(true));

        assertThat(response.getStatus()).isEqualTo(SubjectStatus.INACTIVE);
        assertThat(response.getId()).isEqualTo(14L);
        assertThat(response.getSubjectCode()).isEqualTo("CS101");
        assertThat(response.getCredits()).isEqualTo(3);
        assertThat(response.getPrerequisites()).hasSize(1);
        assertThat(subject.isDeleted()).isFalse();
        assertThat(subject.getUpdatedAt()).isNotNull();
        verify(subjectRepository).save(subject);
    }

    @Test
    void softDeletesSubjectWithoutPhysicallyRemovingIt() {
        Subject subject = subject(14L, "CS101", "Lập trình cơ bản");
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));

        service.deleteSubject(14L, actionRequest(true));

        assertThat(subject.isDeleted()).isTrue();
        assertThat(subject.getSubjectCode()).isEqualTo("CS101");
        assertThat(subject.getUpdatedAt()).isNotNull();
        verify(subjectRepository).save(subject);
        verify(subjectRepository, never()).delete(any(Subject.class));
        verify(subjectPrerequisiteRepository, never()).saveAll(any());
        verify(subjectPrerequisiteRepository, never()).delete(any(SubjectPrerequisite.class));
    }

    @Test
    void blocksDeactivationAndListsActiveDependentSubjects() {
        Subject subject = subject(14L, "CS101", "Lập trình cơ bản");
        when(subjectPrerequisiteRepository.findAllByPrerequisiteSubject_IdInAndDeletedFalse(List.of(14L)))
                .thenReturn(List.of(link(subject(20L, "CS201", "Cấu trúc dữ liệu"), subject)));
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));

        assertThatThrownBy(() -> service.deactivateSubject(14L, actionRequest(true)))
                .isInstanceOfSatisfying(
                        SubjectHasDependentsException.class, exception -> assertThat(exception.getDependentSubjects())
                                .singleElement()
                                .satisfies(dependent ->
                                        assertThat(dependent.getSubjectCode()).isEqualTo("CS201")));

        assertThat(subject.getStatus()).isEqualTo(SubjectStatus.ACTIVE);
        assertThat(subject.isDeleted()).isFalse();
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void blocksDeletionWhenAnActiveSubjectDependsOnIt() {
        Subject subject = subject(14L, "CS101", "Lập trình cơ bản");
        when(subjectPrerequisiteRepository.findAllByPrerequisiteSubject_IdInAndDeletedFalse(List.of(14L)))
                .thenReturn(List.of(link(subject(20L, "CS201", "Cấu trúc dữ liệu"), subject)));
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));

        assertThatThrownBy(() -> service.deleteSubject(14L, actionRequest(true)))
                .isInstanceOf(SubjectHasDependentsException.class);

        assertThat(subject.isDeleted()).isFalse();
        verify(subjectRepository, never()).save(any());
    }

    @Test
    void cancelledDeactivationDoesNotChangeData() {
        assertThatThrownBy(() -> service.deactivateSubject(14L, actionRequest(false)))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SUBJECT_ACTION_NOT_CONFIRMED));

        verifyNoInteractions(subjectRepository, subjectPrerequisiteRepository);
    }

    @Test
    void cancelledDeletionDoesNotChangeData() {
        assertThatThrownBy(() -> service.deleteSubject(14L, actionRequest(false)))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SUBJECT_ACTION_NOT_CONFIRMED));

        verifyNoInteractions(subjectRepository, subjectPrerequisiteRepository);
    }

    @Test
    void rejectsDeletionOfMissingOrAlreadyDeletedSubject() {
        when(subjectRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSubject(99L, actionRequest(true)))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SUBJECT_NOT_FOUND));

        verify(subjectRepository, never()).save(any());
    }

    @Test
    void inactiveOrDeletedDependentsAndDeletedLinksDoNotBlockDeletion() {
        Subject subject = subject(14L, "CS101", "Lập trình cơ bản");
        Subject inactiveDependent = subject(20L, "CS201", "Học phần ngừng sử dụng");
        inactiveDependent.setStatus(SubjectStatus.INACTIVE);
        Subject deletedDependent = subject(21L, "CS202", "Học phần đã xóa");
        deletedDependent.setDeleted(true);
        SubjectPrerequisite deletedLink = link(subject(22L, "CS203", "Học phần đang sử dụng"), subject);
        deletedLink.setDeleted(true);
        when(subjectPrerequisiteRepository.findAllByPrerequisiteSubject_IdInAndDeletedFalse(List.of(14L)))
                .thenReturn(List.of(link(inactiveDependent, subject), link(deletedDependent, subject), deletedLink));
        when(subjectRepository.findByIdAndDeletedFalse(14L)).thenReturn(Optional.of(subject));

        service.deleteSubject(14L, actionRequest(true));

        assertThat(subject.isDeleted()).isTrue();
        verify(subjectRepository).save(subject);
        verify(subjectPrerequisiteRepository, never()).saveAll(any());
    }

    private SubjectActionRequest actionRequest(boolean confirmed) {
        var request = new SubjectActionRequest();
        request.setReason("Không còn sử dụng trong danh mục mới");
        request.setConfirmed(confirmed);
        return request;
    }

    private UpdateSubjectRequest updateRequest() {
        var request = new UpdateSubjectRequest();
        request.setSubjectName("Lập trình nâng cao");
        request.setCredits(4);
        request.setDescription("Mô tả cập nhật");
        return request;
    }

    private SubjectPrerequisite link(Subject subject, Subject prerequisite) {
        return SubjectPrerequisite.builder()
                .subject(subject)
                .prerequisiteSubject(prerequisite)
                .build();
    }

    private Subject subject(Long id, String code, String name) {
        Subject subject = Subject.builder()
                .subjectCode(code)
                .subjectName(name)
                .credits(3)
                .description("Mô tả học phần")
                .status(SubjectStatus.ACTIVE)
                .build();
        subject.setId(id);
        return subject;
    }

    private CreateSubjectRequest validRequest() {
        var request = new CreateSubjectRequest();
        request.setSubjectCode("CS101");
        request.setSubjectName("Lập trình cơ bản");
        request.setCredits(3);
        request.setDescription("Kiến thức nhập môn lập trình");
        return request;
    }
}
