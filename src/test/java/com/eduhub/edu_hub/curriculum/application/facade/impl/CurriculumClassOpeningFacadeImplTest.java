package com.eduhub.edu_hub.curriculum.application.facade.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.domain.repository.CurriculumSubjectRepository;
import com.eduhub.edu_hub.curriculum.domain.repository.SubjectRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CurriculumClassOpeningFacadeImplTest {
    private final CurriculumSubjectRepository curriculumSubjectRepository = mock(CurriculumSubjectRepository.class);
    private final SubjectRepository subjectRepository = mock(SubjectRepository.class);
    private final CurriculumClassOpeningFacadeImpl facade =
            new CurriculumClassOpeningFacadeImpl(curriculumSubjectRepository, subjectRepository);

    @Test
    void subjectKeywordIsNormalizedAndEscapedBeforeSearchingHistoricalIds() {
        when(subjectRepository.findSubjectIdsByKeyword("%cs!_!%!!\\%")).thenReturn(Set.of(11L, 12L));

        assertThat(facade.findSubjectIdsByKeyword(" CS_%!\\ ")).containsExactlyInAnyOrder(11L, 12L);

        verify(subjectRepository).findSubjectIdsByKeyword("%cs!_!%!!\\%");
        verifyNoMoreInteractions(subjectRepository);
        verifyNoInteractions(curriculumSubjectRepository);
    }

    @Test
    void emptyKeywordDoesNotQuerySubjects() {
        assertThat(facade.findSubjectIdsByKeyword(null)).isEmpty();
        assertThat(facade.findSubjectIdsByKeyword("  ")).isEmpty();
        verifyNoInteractions(subjectRepository, curriculumSubjectRepository);
    }
}
