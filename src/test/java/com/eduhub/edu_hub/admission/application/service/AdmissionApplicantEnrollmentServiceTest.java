package com.eduhub.edu_hub.admission.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.application.exception.AdmissionEnrollmentException;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionIdentityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.StudentAdmissionRepository;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Kiểm tra chặn hồ sơ đã bỏ tại bước nhập học bằng mock; không xác nhận bộ lọc JPA trên database thật. */
class AdmissionApplicantEnrollmentServiceTest {
    @Test
    void rejectsAnApplicantOmittedByTheActiveRowQueryBeforeRegisteringStudentsOrNotifications() {
        var applicants = mock(AdmissionApplicantRepository.class);
        var admissions = mock(StudentAdmissionRepository.class);
        var notifications = mock(NotificationFacade.AdmissionQueue.class);
        var people = mock(PeopleFacade.Admission.class);
        var lease = mock(AdmissionConfirmationLeaseService.class);
        when(lease.requireActiveConfirmation(7L, "confirmation-token"))
                .thenReturn(AdmissionPlan.builder().build());
        when(applicants.findByIdAndDeletedFalse(11L)).thenReturn(Optional.empty());
        var service = new AdmissionApplicantEnrollmentService(
                applicants, admissions, notifications, people, new AdmissionIdentityPolicy(), lease);

        assertThatThrownBy(() -> service.enroll(7L, "confirmation-token", 11L))
                .isInstanceOfSatisfying(
                        AdmissionEnrollmentException.class, exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMISSION_APPLICANT_NOT_FOUND));

        verifyNoInteractions(admissions, people, notifications);
        verify(applicants).findByIdAndDeletedFalse(11L);
    }
}
