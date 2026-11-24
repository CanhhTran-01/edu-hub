package com.eduhub.edu_hub.curriculum.application.facade.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.domain.enums.CatalogStatus;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.curriculum.domain.repository.CurriculumRepository;
import com.eduhub.edu_hub.curriculum.domain.repository.MajorRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class CurriculumReferenceDataFacadeImplTest {
    private final MajorRepository majorRepository = mock(MajorRepository.class);
    private final CurriculumRepository curriculumRepository = mock(CurriculumRepository.class);
    private final CurriculumReferenceDataFacadeImpl facade =
            new CurriculumReferenceDataFacadeImpl(majorRepository, curriculumRepository);

    @Test
    void historicalMajorNamesAndDeletionStateAreReadInBatchesWithoutFacultyEntities() {
        var majorIds = LongStream.rangeClosed(1, 501).boxed().collect(Collectors.toSet());
        var firstBatch = LongStream.rangeClosed(1, 500).boxed().toList();
        var hiddenMajor = Major.builder()
                .majorCode("OLD")
                .majorName("Ngành cũ")
                .facultyId(7L)
                .status(CatalogStatus.INACTIVE)
                .build();
        hiddenMajor.setId(1L);
        hiddenMajor.setDeleted(true);
        var currentMajor =
                Major.builder().majorCode("NEW").majorName("Ngành mới").build();
        currentMajor.setId(501L);
        when(majorRepository.findAllById(firstBatch)).thenReturn(List.of(hiddenMajor));
        when(majorRepository.findAllById(List.of(501L))).thenReturn(List.of(currentMajor));

        var majors = facade.findMajorsByIds(majorIds);

        assertThat(majors).hasSize(2);
        assertThat(majors.getFirst().majorName()).isEqualTo("Ngành cũ");
        assertThat(majors.getFirst().facultyId()).isEqualTo(7L);
        assertThat(majors.getFirst().deleted()).isTrue();
        assertThat(majors.getLast().deleted()).isFalse();
        verify(majorRepository).findAllById(firstBatch);
        verify(majorRepository).findAllById(List.of(501L));
        verifyNoMoreInteractions(majorRepository);
        verifyNoInteractions(curriculumRepository);
    }

    @Test
    void emptyMajorIdsDoNotQueryCurriculum() {
        assertThat(facade.findMajorsByIds(Set.of())).isEmpty();
        verifyNoInteractions(majorRepository, curriculumRepository);
    }
}
