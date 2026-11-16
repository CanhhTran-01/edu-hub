package com.eduhub.edu_hub.enrollment.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.people.api.dto.response.StudentRosterProfileResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnrollmentClassRosterFacadeImplTest {
    private static final Set<EnrollmentStatus> ROSTER_STATUSES =
            Set.of(EnrollmentStatus.REGISTERED, EnrollmentStatus.COMPLETED, EnrollmentStatus.FAILED);
    private final EnrollmentRepository repository = mock(EnrollmentRepository.class);
    private final PeopleFacade.Enrollment people = mock(PeopleFacade.Enrollment.class);
    private final EnrollmentClassRosterFacadeImpl facade = new EnrollmentClassRosterFacadeImpl(repository, people);

    @Test
    void includesRegisteredPassedAndFailedAttemptsWithStoredScoresAndSortsStudentCodes() {
        var registered = enrollment(10L, 1L, EnrollmentStatus.REGISTERED);
        var completed = enrollment(11L, 2L, EnrollmentStatus.COMPLETED);
        var failed = enrollment(12L, 3L, EnrollmentStatus.FAILED);
        when(repository.findAllByModuleClassIdAndDeletedFalseAndStatusInOrderByIdAsc(100L, ROSTER_STATUSES))
                .thenReturn(List.of(registered, completed, failed));
        when(people.findRosterStudentsByIds(Set.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        new StudentRosterProfileResponse(1L, "SV003", "Nguyễn Văn Ba"),
                        new StudentRosterProfileResponse(2L, "00001", "Nguyễn Văn Một"),
                        new StudentRosterProfileResponse(3L, "SV002", "Nguyễn Văn Hai")));

        var students = facade.findStudentsByModuleClassId(100L);

        assertThat(students).extracting(student -> student.studentCode()).containsExactly("00001", "SV002", "SV003");
        assertThat(students).allSatisfy(student -> {
            assertThat(student.componentScore1()).isEqualByComparingTo("0");
            assertThat(student.componentScore2()).isNull();
            assertThat(student.processScore()).isEqualByComparingTo("6.7");
            assertThat(student.note()).isEmpty();
        });
        // Truy vấn có điều kiện chưa xóa và đúng tập trạng thái; không dùng truy vấn mọi lượt rồi lộ lượt hủy.
        verify(repository, never()).findAllByModuleClassIdOrderByIdAsc(anyLong());
    }

    @Test
    void emptyClassDoesNotLoadStudentProfiles() {
        when(repository.findAllByModuleClassIdAndDeletedFalseAndStatusInOrderByIdAsc(100L, ROSTER_STATUSES))
                .thenReturn(List.of());
        assertThat(facade.findStudentsByModuleClassId(100L)).isEmpty();
        verifyNoInteractions(people);
    }

    @Test
    void missingProfileFailsInsteadOfSilentlyDroppingAStudent() {
        when(repository.findAllByModuleClassIdAndDeletedFalseAndStatusInOrderByIdAsc(100L, ROSTER_STATUSES))
                .thenReturn(List.of(enrollment(10L, 1L, EnrollmentStatus.REGISTERED)));
        when(people.findRosterStudentsByIds(Set.of(1L))).thenReturn(List.of());
        assertThatThrownBy(() -> facade.findStudentsByModuleClassId(100L))
                .isInstanceOfSatisfying(EnrollmentException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ENROLLMENT_DATA_INCOMPLETE));
    }

    @Test
    void readsTheNoteStoredOnTheLearningAttempt() {
        var enrollment = enrollment(10L, 1L, EnrollmentStatus.REGISTERED);
        enrollment.updateProcessScores(BigDecimal.ZERO, null, null, "Đã đối chiếu bài tập", null);
        when(repository.findAllByModuleClassIdAndDeletedFalseAndStatusInOrderByIdAsc(100L, ROSTER_STATUSES))
                .thenReturn(List.of(enrollment));
        when(people.findRosterStudentsByIds(Set.of(1L)))
                .thenReturn(List.of(new StudentRosterProfileResponse(1L, "00001", "Nguyễn Văn Một")));
        assertThat(facade.findStudentsByModuleClassId(100L).getFirst().note()).isEqualTo("Đã đối chiếu bài tập");
    }

    private Enrollment enrollment(Long enrollmentId, Long studentId, EnrollmentStatus status) {
        var enrollment = Enrollment.builder()
                .studentId(studentId)
                .moduleClassId(100L)
                .status(status)
                .componentScore1(BigDecimal.ZERO)
                .processScore(new BigDecimal("6.7"))
                .build();
        enrollment.setId(enrollmentId);
        return enrollment;
    }
}
