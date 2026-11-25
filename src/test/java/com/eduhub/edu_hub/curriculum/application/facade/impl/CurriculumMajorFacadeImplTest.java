package com.eduhub.edu_hub.curriculum.application.facade.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.curriculum.api.dto.response.MajorReferenceResponse;
import com.eduhub.edu_hub.curriculum.application.exception.MajorReferenceException;
import com.eduhub.edu_hub.curriculum.domain.enums.CatalogStatus;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.curriculum.domain.repository.MajorRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CurriculumMajorFacadeImplTest {
    private final MajorRepository majorRepository = mock(MajorRepository.class);
    private final CurriculumMajorFacadeImpl facade = new CurriculumMajorFacadeImpl(majorRepository);

    @Test
    void requiresUndeletedMajorWithoutFilteringItsCatalogStatus() {
        Major major = major(7L, "CNTT", "Công nghệ thông tin", CatalogStatus.INACTIVE);
        when(majorRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(major));
        assertThat(facade.requireMajor(7L)).isEqualTo(new MajorReferenceResponse(7L, "CNTT", "Công nghệ thông tin"));
        verify(majorRepository).findByIdAndDeletedFalse(7L);
    }

    @Test
    void missingMajorKeepsItsExistingBusinessErrorCode() {
        when(majorRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.requireMajor(7L))
                .isInstanceOfSatisfying(MajorReferenceException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STUDENT_CODE_MAJOR));
    }

    @Test
    void classNamingKeepsReferenceFieldsAndRepositoryOrder() {
        when(majorRepository.findByDeletedFalseOrderById())
                .thenReturn(List.of(
                        major(3L, "CNTT", "Công nghệ thông tin", CatalogStatus.ACTIVE),
                        major(7L, "ATTT", "An toàn thông tin", CatalogStatus.INACTIVE)));
        assertThat(facade.findMajorsForClassNaming())
                .containsExactly(
                        new MajorReferenceResponse(3L, "CNTT", "Công nghệ thông tin"),
                        new MajorReferenceResponse(7L, "ATTT", "An toàn thông tin"));
    }

    private Major major(Long majorId, String majorCode, String majorName, CatalogStatus status) {
        Major major = Major.builder()
                .majorCode(majorCode)
                .majorName(majorName)
                .status(status)
                .build();
        major.setId(majorId);
        return major;
    }
}
