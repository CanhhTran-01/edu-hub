package com.eduhub.edu_hub.curriculum.application.facade.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.domain.enums.*;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.*;
import com.eduhub.edu_hub.curriculum.domain.repository.CurriculumRepository;
import com.eduhub.edu_hub.curriculum.domain.repository.CurriculumSubjectRepository;
import com.eduhub.edu_hub.people.api.dto.response.CohortResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurriculumCatalogFacadeImplTest {
    @Mock
    private CurriculumRepository curriculums;

    @Mock
    private CurriculumSubjectRepository links;

    @Mock
    private PeopleFacade.Cohorts peopleCohorts;

    @InjectMocks
    private CurriculumCatalogFacadeImpl facade;

    @Test
    void catalogCreditsChangeWithSubjectsButGraduationRequirementRemainsIndependent() {
        var major = Major.builder().majorName("Ngành CNTT").build();
        major.setId(1L);
        when(peopleCohorts.findCohortsByIds(Set.of(5L)))
                .thenReturn(List.of(new CohortResponse(5L, "K2026", null, null)));
        var curriculum = Curriculum.builder()
                .curriculumCode("CNTT_2026")
                .major(major)
                .cohortId(5L)
                .trainingType(TrainingType.CHINH_QUY)
                .requiredGraduationCredits(120)
                .build();
        curriculum.setId(10L);
        var required = subject(1L, 3);
        var elective = subject(2L, 4);
        elective.setStatus(SubjectStatus.INACTIVE);
        var hidden = subject(3L, 9);
        hidden.setDeleted(true);
        when(curriculums.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(curriculum));
        when(links.findAllByCurriculum_IdAndDeletedFalseOrderByRecommendSemesterAscIdAsc(10L))
                .thenReturn(List.of(
                        link(required, SubjectType.REQUIRED),
                        link(elective, SubjectType.ELECTIVE),
                        link(hidden, SubjectType.REQUIRED)));

        var before = facade.findById(10L).orElseThrow();
        assertThat(before.getTotalCredits()).isEqualTo(7);
        assertThat(before.getSubjects()).hasSize(2);
        assertThat(before.getRequiredGraduationCredits()).isEqualTo(120);
        assertThat(before.getCohortCode()).isEqualTo("K2026");
        assertThat(before.getCohortId()).isEqualTo(5L);

        required.setCredits(5);
        var after = facade.findById(10L).orElseThrow();
        assertThat(after.getTotalCredits()).isEqualTo(9);
        assertThat(after.getRequiredGraduationCredits()).isEqualTo(120);
    }

    @Test
    void lookupIncludesTrainingTypeAndMissingCurriculumDoesNotLoadSubjects() {
        assertThat(facade.findCurriculum(1L, 5L, TrainingType.LIEN_THONG)).isEmpty();
        verify(curriculums).findByMajor_IdAndCohortIdAndTrainingTypeAndDeletedFalse(1L, 5L, TrainingType.LIEN_THONG);
        verifyNoInteractions(links, peopleCohorts);
    }

    @Test
    void legacyCurriculumWithoutACohortAndHiddenCohortAreHandled() {
        var curriculum = Curriculum.builder()
                .major(Major.builder().majorName("CNTT").build())
                .trainingType(TrainingType.CHINH_QUY)
                .requiredGraduationCredits(120)
                .build();
        curriculum.setId(10L);
        when(curriculums.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(curriculum));
        assertThat(facade.findById(10L).orElseThrow().getCohortId()).isNull();
        verifyNoInteractions(peopleCohorts);

        when(peopleCohorts.findCohortsByIds(Set.of(5L))).thenReturn(List.of());
        var historical = Curriculum.builder()
                .major(curriculum.getMajor())
                .cohortId(5L)
                .trainingType(TrainingType.CHINH_QUY)
                .requiredGraduationCredits(120)
                .build();
        historical.setId(10L);
        when(curriculums.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(historical));
        var response = facade.findById(10L).orElseThrow();
        assertThat(response.getCohortId()).isEqualTo(5L);
        assertThat(response.getCohortCode()).isNull();
    }

    private Subject subject(long id, int credits) {
        var subject = Subject.builder()
                .subjectCode("S" + id)
                .credits(credits)
                .status(SubjectStatus.ACTIVE)
                .build();
        subject.setId(id);
        return subject;
    }

    private CurriculumSubject link(Subject subject, SubjectType type) {
        return CurriculumSubject.builder()
                .subject(subject)
                .subjectType(type)
                .recommendSemester(1)
                .build();
    }
}
