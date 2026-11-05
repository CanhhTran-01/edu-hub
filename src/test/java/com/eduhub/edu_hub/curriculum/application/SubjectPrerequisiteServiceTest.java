package com.eduhub.edu_hub.curriculum.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.curriculum.api.dto.request.SetPrerequisiteRequest;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.exception.PrerequisiteCycleException;
import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.curriculum.domain.model.SubjectPrerequisite;
import com.eduhub.edu_hub.curriculum.domain.repository.SubjectPrerequisiteRepository;
import com.eduhub.edu_hub.curriculum.domain.repository.SubjectRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class SubjectPrerequisiteServiceTest {
    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SubjectPrerequisiteRepository subjectPrerequisiteRepository;

    @InjectMocks
    private SubjectApplicationService service;

    private final List<Subject> subjects = new ArrayList<>();
    private final List<SubjectPrerequisite> links = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(subjectRepository.findAllByDeletedFalseOrderByIdAsc()).thenReturn(subjects);
        // Các request sai ID/trạng thái bị từ chối trước khi đọc đồ thị.
        lenient().when(subjectPrerequisiteRepository.findAllByDeletedFalse()).thenAnswer(invocation -> links.stream()
                .filter(link -> !link.isDeleted())
                .toList());
    }

    @Test
    void addsRemovesAndKeepsPrerequisitesInOneSave() {
        Subject a = subject(1L, "A");
        Subject b = subject(2L, "B");
        Subject c = subject(3L, "C");
        Subject d = subject(4L, "D");
        SubjectPrerequisite removed = link(a, b);
        SubjectPrerequisite kept = link(a, c);
        stubSaving(a);

        var response = service.setPrerequisites(1L, request(3L, 4L));

        assertThat(response.getPrerequisites())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(3L, 4L);
        assertThat(removed.isDeleted()).isTrue();
        assertThat(removed.getUpdatedAt()).isNotNull();
        assertThat(kept.isDeleted()).isFalse();
        assertThat(links).hasSize(3);
        assertThat(links)
                .filteredOn(link -> link.getPrerequisiteSubject() == d)
                .singleElement()
                .satisfies(added -> {
                    assertThat(added.getSubject()).isSameAs(a);
                    assertThat(added.getCreatedAt()).isNotNull();
                });
        verify(subjectPrerequisiteRepository).saveAll(List.of(removed, links.getLast()));
        verify(subjectRepository).save(a);
    }

    @Test
    void emptyListSoftDeletesAllCurrentPrerequisites() {
        Subject a = subject(1L, "A");
        Subject b = subject(2L, "B");
        SubjectPrerequisite old = link(a, b);
        stubSaving(a);

        var response = service.setPrerequisites(1L, request());

        assertThat(response.getPrerequisites()).isEmpty();
        assertThat(old.isDeleted()).isTrue();
        assertThat(links).containsExactly(old);
        verify(subjectPrerequisiteRepository).saveAll(List.of(old));
        verify(subjectPrerequisiteRepository, never()).delete(any(SubjectPrerequisite.class));
    }

    @Test
    void ignoresDuplicateIdsAndExistingEdges() {
        Subject a = subject(1L, "A");
        Subject b = subject(2L, "B");
        SubjectPrerequisite existing = link(a, b);
        stubSaving(a);

        var response = service.setPrerequisites(1L, request(2L, 2L, 2L));

        assertThat(response.getPrerequisites()).hasSize(1);
        assertThat(links).containsExactly(existing);
        assertThat(existing.isDeleted()).isFalse();
    }

    @Test
    void reAddingRemovedPrerequisiteKeepsTheDeletedLinkAsHistory() {
        Subject a = subject(1L, "A");
        Subject b = subject(2L, "B");
        SubjectPrerequisite old = link(a, b);
        old.setDeleted(true);
        stubSaving(a);

        var response = service.setPrerequisites(1L, request(2L));

        assertThat(old.isDeleted()).isTrue();
        assertThat(links).hasSize(2);
        assertThat(links.getLast()).isNotSameAs(old);
        assertThat(links.getLast().isDeleted()).isFalse();
        assertThat(response.getPrerequisites())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(2L);
    }

    @Test
    void rejectsSelfReferenceWithoutChangingTheExistingLinks() {
        Subject a = subject(1L, "A");
        SubjectPrerequisite old = link(a, subject(2L, "B"));

        assertThatThrownBy(() -> service.setPrerequisites(1L, request(1L)))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PREREQUISITE_SELF_REFERENCE));

        assertThat(old.isDeleted()).isFalse();
        assertThat(a.getUpdatedAt()).isNull();
        verify(subjectRepository, never()).save(any());
        verify(subjectPrerequisiteRepository, never()).saveAll(any());
    }

    @Test
    void rejectsTwoNodeCycleAndReturnsItsPath() {
        Subject a = subject(1L, "A");
        Subject b = subject(2L, "B");
        SubjectPrerequisite old = link(b, a);

        assertThatThrownBy(() -> service.setPrerequisites(1L, request(2L)))
                .isInstanceOfSatisfying(PrerequisiteCycleException.class, exception -> assertThat(exception.getCycle())
                        .extracting(SubjectSimpleResponse::getSubjectCode)
                        .containsExactly("A", "B", "A"));

        assertThat(links).containsExactly(old);
        assertThat(old.isDeleted()).isFalse();
        verify(subjectRepository, never()).save(any());
        verify(subjectPrerequisiteRepository, never()).saveAll(any());
    }

    @Test
    void rejectsLongCycleEvenWhenAnIntermediateSubjectIsInactive() {
        Subject a = subject(1L, "A");
        Subject b = subject(2L, "B");
        Subject c = subject(3L, "C");
        c.setStatus(SubjectStatus.INACTIVE);
        link(b, c);
        link(c, a);

        assertThatThrownBy(() -> service.setPrerequisites(1L, request(2L)))
                .isInstanceOfSatisfying(PrerequisiteCycleException.class, exception -> assertThat(exception.getCycle())
                        .extracting(SubjectSimpleResponse::getSubjectCode)
                        .containsExactly("A", "B", "C", "A"));

        assertThat(links).hasSize(2).allSatisfy(link -> assertThat(link.isDeleted())
                .isFalse());
        verify(subjectRepository, never()).save(any());
        verify(subjectPrerequisiteRepository, never()).saveAll(any());
    }

    @Test
    void allowsDifferentBranchesToShareTheSamePrerequisite() {
        Subject a = subject(1L, "A");
        Subject b = subject(2L, "B");
        Subject c = subject(3L, "C");
        Subject d = subject(4L, "D");
        link(b, d);
        link(c, d);
        stubSaving(a);

        var response = service.setPrerequisites(1L, request(2L, 3L));

        assertThat(response.getPrerequisites())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(2L, 3L);
    }

    @Test
    void stillChecksCyclesWhenTheRequestedListIsEmpty() {
        subject(1L, "A");
        Subject b = subject(2L, "B");
        Subject c = subject(3L, "C");
        link(b, c);
        link(c, b);

        assertThatThrownBy(() -> service.setPrerequisites(1L, request()))
                .isInstanceOfSatisfying(PrerequisiteCycleException.class, exception -> assertThat(exception.getCycle())
                        .extracting(SubjectSimpleResponse::getSubjectCode)
                        .containsExactly("B", "C", "B"));

        verify(subjectRepository, never()).save(any());
        verify(subjectPrerequisiteRepository, never()).saveAll(any());
    }

    @Test
    void allowsRemovingAnEdgeThatBreaksAnExistingCycle() {
        Subject a = subject(1L, "A");
        Subject b = subject(2L, "B");
        SubjectPrerequisite removed = link(a, b);
        SubjectPrerequisite kept = link(b, a);
        stubSaving(a);

        var response = service.setPrerequisites(1L, request());

        assertThat(response.getPrerequisites()).isEmpty();
        assertThat(removed.isDeleted()).isTrue();
        assertThat(kept.isDeleted()).isFalse();
    }

    @Test
    void deletedEdgesDoNotCreateCycles() {
        Subject a = subject(1L, "A");
        Subject b = subject(2L, "B");
        link(b, a).setDeleted(true);
        stubSaving(a);

        var response = service.setPrerequisites(1L, request(2L));

        assertThat(response.getPrerequisites())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(2L);
        assertThat(response.getRequiredBy()).isEmpty();
    }

    @Test
    void softDeletesDuplicateStoredEdgesAndKeepsOneActiveLink() {
        Subject a = subject(1L, "A");
        Subject b = subject(2L, "B");
        SubjectPrerequisite kept = link(a, b);
        SubjectPrerequisite duplicate = link(a, b);
        stubSaving(a);

        var response = service.setPrerequisites(1L, request(2L));

        assertThat(response.getPrerequisites())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(2L);
        assertThat(kept.isDeleted()).isFalse();
        assertThat(duplicate.isDeleted()).isTrue();
        assertThat(links).containsExactly(kept, duplicate);
        verify(subjectPrerequisiteRepository).saveAll(List.of(duplicate));
    }

    @Test
    void ignoresDeletedSubjectsWhenCheckingCyclesAndReturningDependents() {
        Subject a = subject(1L, "A");
        Subject b = subject(2L, "B");
        Subject deleted = subject(3L, "DELETED");
        deleted.setDeleted(true);
        subjects.remove(deleted);
        link(b, deleted);
        link(deleted, a);
        stubSaving(a);

        var response = service.setPrerequisites(1L, request(2L));

        assertThat(response.getPrerequisites())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(2L);
        assertThat(response.getRequiredBy()).isEmpty();
    }

    @Test
    void rejectsInactivePrerequisite() {
        subject(1L, "A");
        subject(2L, "B").setStatus(SubjectStatus.INACTIVE);

        assertThatThrownBy(() -> service.setPrerequisites(1L, request(2L)))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PREREQUISITE_NOT_ACTIVE));

        verify(subjectRepository, never()).save(any());
        verify(subjectPrerequisiteRepository, never()).saveAll(any());
    }

    @Test
    void rejectsInactiveTargetSubject() {
        subject(1L, "A").setStatus(SubjectStatus.INACTIVE);

        assertThatThrownBy(() -> service.setPrerequisites(1L, request()))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SUBJECT_NOT_ACTIVE));

        verify(subjectRepository, never()).save(any());
        verify(subjectPrerequisiteRepository, never()).saveAll(any());
    }

    @Test
    void rejectsMissingOrDeletedPrerequisite() {
        subject(1L, "A");

        assertThatThrownBy(() -> service.setPrerequisites(1L, request(99L)))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SUBJECT_NOT_FOUND));

        verify(subjectRepository, never()).save(any());
        verify(subjectPrerequisiteRepository, never()).saveAll(any());
    }

    @Test
    void rejectsMissingOrDeletedTargetSubject() {
        assertThatThrownBy(() -> service.setPrerequisites(99L, request()))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SUBJECT_NOT_FOUND));

        verify(subjectRepository, never()).save(any());
        verify(subjectPrerequisiteRepository, never()).saveAll(any());
    }

    private void stubSaving(Subject subject) {
        when(subjectRepository.save(subject)).thenReturn(subject);
        when(subjectPrerequisiteRepository.saveAll(any())).thenAnswer(invocation -> {
            List<SubjectPrerequisite> changed = invocation.getArgument(0);
            for (SubjectPrerequisite link : changed) {
                if (!links.contains(link)) {
                    links.add(link);
                }
            }
            return changed;
        });
        when(subjectPrerequisiteRepository.findAllBySubject_IdInAndDeletedFalse(List.of(subject.getId())))
                .thenAnswer(invocation -> links.stream()
                        .filter(link -> !link.isDeleted() && link.getSubject() == subject)
                        .toList());
        when(subjectPrerequisiteRepository.findAllByPrerequisiteSubject_IdInAndDeletedFalse(List.of(subject.getId())))
                .thenAnswer(invocation -> links.stream()
                        .filter(link -> !link.isDeleted() && link.getPrerequisiteSubject() == subject)
                        .toList());
    }

    private Subject subject(Long id, String code) {
        Subject subject = Subject.builder()
                .subjectCode(code)
                .subjectName("Học phần " + code)
                .credits(3)
                .status(SubjectStatus.ACTIVE)
                .build();
        subject.setId(id);
        subjects.add(subject);
        return subject;
    }

    private SubjectPrerequisite link(Subject subject, Subject prerequisite) {
        SubjectPrerequisite link = SubjectPrerequisite.builder()
                .subject(subject)
                .prerequisiteSubject(prerequisite)
                .build();
        links.add(link);
        return link;
    }

    private SetPrerequisiteRequest request(Long... ids) {
        var request = new SetPrerequisiteRequest();
        request.setPrerequisiteSubjectIds(List.of(ids));
        return request;
    }
}
