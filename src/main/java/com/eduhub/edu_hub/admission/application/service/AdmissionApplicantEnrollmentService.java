package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.model.StudentAdmission;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionIdentityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.StudentAdmissionRepository;
import com.eduhub.edu_hub.notification.application.command.QueueAdmissionNotificationCommand;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentRegistration;
import com.eduhub.edu_hub.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class AdmissionApplicantEnrollmentService {
    private final AdmissionApplicantRepository applicantRepository;
    private final StudentAdmissionRepository studentAdmissionRepository;
    private final NotificationFacade.AdmissionQueue notificationQueue;
    private final PeopleFacade.Admission peopleAdmissionFacade;
    private final AdmissionIdentityPolicy identityPolicy;
    private final AdmissionConfirmationLease confirmationLease;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enroll(Long planId, String confirmationToken, Long applicantId) {
        var plan = confirmationLease.requireActiveConfirmation(planId, confirmationToken);
        var applicant = applicantRepository.findById(applicantId).orElseThrow();
        if (!applicant.getPlan().getId().equals(planId)) throw new BaseException(ErrorCode.VALIDATION_ERROR);
        if (!applicant.isPendingEnrollment()) return;
        var existingAdmission = studentAdmissionRepository.findByApplicationCode(applicant.getApplicationCode());
        if (existingAdmission.isPresent()) {
            if (!identityPolicy.hasSameApplicantData(applicant, existingAdmission.get()))
                throw new BaseException(ErrorCode.ADMISSION_APPLICATION_CONFLICT);
            applicant.setStudentId(existingAdmission.get().getStudentId());
            applicant.setReuseAccount(true);
            applicant.setState(AdmissionApplicantStatus.EXISTING);
            applicant.setErrorMessage("Hồ sơ đã tồn tại; không cấp thêm MSSV hoặc tài khoản");
            confirmationLease.extendConfirmationLease(plan);
            return;
        }
        var studentRegistration = peopleAdmissionFacade.registerStudent(AdmissionStudentRegistration.builder()
                .classId(applicant.getAdministrativeClassId())
                .citizenId(applicant.getCitizenId())
                .fullName(applicant.getFullName())
                .dateOfBirth(applicant.getDateOfBirth())
                .gender(applicant.getGender())
                .email(applicant.getEmail())
                .phone(applicant.getPhone())
                .entranceScore(applicant.getEntranceScore())
                .build());
        Long studentId = studentRegistration.student().getId();
        if (studentRegistration.accountLocked())
            applicant.setWarningMessage("Tài khoản cũ đang khóa/ngừng hoạt động; giữ nguyên trạng thái");
        var admission = StudentAdmission.builder()
                .applicationCode(applicant.getApplicationCode())
                .studentId(studentId)
                .plan(plan)
                .administrativeClassId(applicant.getAdministrativeClassId())
                .citizenId(applicant.getCitizenId())
                .fullName(applicant.getFullName())
                .dateOfBirth(applicant.getDateOfBirth())
                .gender(applicant.getGender())
                .email(applicant.getEmail())
                .phone(applicant.getPhone())
                .entranceScore(applicant.getEntranceScore())
                .build();
        studentAdmissionRepository.saveAndFlush(admission);
        notificationQueue.enqueueAdmissionNotification(QueueAdmissionNotificationCommand.builder()
                .admissionId(admission.getId())
                .studentId(studentId)
                .recipient(applicant.getEmail())
                .reEnrollment(studentRegistration.reused())
                .build());
        applicant.setStudentId(studentId);
        applicant.setReuseAccount(studentRegistration.reused());
        applicant.setState(AdmissionApplicantStatus.ENROLLED);
        applicant.setErrorMessage(null);
        confirmationLease.extendConfirmationLease(plan);
        // TODO(AUDIT_LOG): Ghi kết quả từng hồ sơ và việc dùng lại danh tính sau khi commit.
        // Hàng đợi thuộc notification nhưng tham gia cùng transaction; listener chỉ gửi sau commit.
    }
}
