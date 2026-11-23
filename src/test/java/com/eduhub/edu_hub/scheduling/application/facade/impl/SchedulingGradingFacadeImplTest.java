package com.eduhub.edu_hub.scheduling.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.scheduling.application.exception.SemesterException;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SchedulingGradingFacadeImplTest {
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final ModuleClassRepository classes = mock(ModuleClassRepository.class);
    private final SchedulingGradingFacadeImpl facade = new SchedulingGradingFacadeImpl(semesters, classes);

    @Test
    void locksSemesterBeforeLoadingTheAssignedClassAndReturnsOnlyTheGradingContext() {
        var semester = Semester.builder().semesterCode("HK_1_2026_2027").build();
        semester.setId(1L);
        var lecturer = mock(Lecturer.class);
        when(lecturer.getId()).thenReturn(70L);
        var moduleClass = ModuleClass.builder()
                .semester(semester)
                .lecturer(lecturer)
                .moduleClassCode("MC_100")
                .status(ModuleClassStatus.COMPLETED)
                .build();
        moduleClass.setId(100L);
        when(classes.findSemesterIdByIdAndDeletedFalse(100L)).thenReturn(Optional.of(1L));
        when(semesters.findForRegistrationByIdAndDeletedFalse(1L)).thenReturn(Optional.of(semester));
        when(classes.findForUpdateByIdAndDeletedFalse(100L)).thenReturn(Optional.of(moduleClass));

        var response = facade.lockModuleClassForGrading(100L);

        assertThat(response.lecturerId()).isEqualTo(70L);
        assertThat(response.status()).isEqualTo(ModuleClassStatus.COMPLETED);
        var order = inOrder(classes, semesters);
        order.verify(classes).findSemesterIdByIdAndDeletedFalse(100L);
        order.verify(semesters).findForRegistrationByIdAndDeletedFalse(1L);
        order.verify(classes).findForUpdateByIdAndDeletedFalse(100L);
        verify(classes, never()).findByIdAndDeletedFalse(100L);
    }

    @Test
    void rejectsADeletedSemesterWithoutLoadingTheClass() {
        when(classes.findSemesterIdByIdAndDeletedFalse(100L)).thenReturn(Optional.of(1L));
        when(semesters.findForRegistrationByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.lockModuleClassForGrading(100L))
                .isInstanceOfSatisfying(SemesterException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SEMESTER_NOT_FOUND));
        verify(classes, never()).findForUpdateByIdAndDeletedFalse(100L);
    }
}
