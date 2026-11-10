package com.eduhub.edu_hub.curriculum.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.exception.TeachingSubjectException;
import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.curriculum.domain.repository.SubjectRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CurriculumTeachingSubjectsFacadeImplTest {
    private final SubjectRepository subjectRepository = mock(SubjectRepository.class);
    private final CurriculumTeachingSubjectsFacadeImpl teachingSubjectsFacade =
            new CurriculumTeachingSubjectsFacadeImpl(subjectRepository);

    @Test
    void requiresAllRequestedSubjectsToExistAndBeActive() {
        when(subjectRepository.findAllByIdInAndDeletedFalseOrderByIdAsc(Set.of(1L, 2L)))
                .thenReturn(List.of(subject(1L, SubjectStatus.ACTIVE), subject(2L, SubjectStatus.ACTIVE)));
        assertThat(teachingSubjectsFacade.requireActiveTeachingSubjects(Set.of(1L, 2L)))
                .extracting(subject -> subject.getId())
                .containsExactly(1L, 2L);
        verify(subjectRepository).findAllByIdInAndDeletedFalseOrderByIdAsc(Set.of(1L, 2L));
    }

    @Test
    void rejectsMissingOrDeletedSubjectsInsteadOfSavingPartialSelection() {
        when(subjectRepository.findAllByIdInAndDeletedFalseOrderByIdAsc(Set.of(1L, 2L)))
                .thenReturn(List.of(subject(1L, SubjectStatus.ACTIVE)));
        assertThatThrownBy(() -> teachingSubjectsFacade.requireActiveTeachingSubjects(Set.of(1L, 2L)))
                .isInstanceOfSatisfying(
                        TeachingSubjectException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUBJECT_NOT_FOUND));
    }

    @Test
    void inactiveSubjectsCannotBeSelectedForNewLecturers() {
        when(subjectRepository.findAllByIdInAndDeletedFalseOrderByIdAsc(Set.of(1L)))
                .thenReturn(List.of(subject(1L, SubjectStatus.INACTIVE)));
        assertThatThrownBy(() -> teachingSubjectsFacade.requireActiveTeachingSubjects(Set.of(1L)))
                .isInstanceOfSatisfying(
                        TeachingSubjectException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUBJECT_NOT_TEACHABLE));
    }

    @Test
    void historicalReadKeepsInactiveAndDeletedSubjectsWithoutAllowingNewSelection() {
        Subject hiddenSubject = subject(2L, SubjectStatus.INACTIVE);
        hiddenSubject.setDeleted(true);
        when(subjectRepository.findAllById(Set.of(1L, 2L)))
                .thenReturn(List.of(hiddenSubject, subject(1L, SubjectStatus.ACTIVE)));
        assertThat(teachingSubjectsFacade.findTeachingSubjectsByIds(Set.of(1L, 2L)))
                .extracting(subject -> subject.getId())
                .containsExactly(1L, 2L);
    }

    @Test
    void emptySetsDoNotQueryTheRepository() {
        assertThat(teachingSubjectsFacade.findTeachingSubjectsByIds(Set.of())).isEmpty();
        assertThat(teachingSubjectsFacade.requireActiveTeachingSubjects(Set.of()))
                .isEmpty();
        verifyNoInteractions(subjectRepository);
    }

    private Subject subject(Long subjectId, SubjectStatus status) {
        Subject subject = Subject.builder().subjectCode("CS" + subjectId).build();
        subject.setId(subjectId);
        subject.setSubjectName("Học phần " + subjectId);
        subject.setCredits(3);
        subject.setStatus(status);
        return subject;
    }
}
