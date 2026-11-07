package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.model.StudentAdmission;
import com.eduhub.edu_hub.admission.domain.policy.AdmissionIdentityPolicy;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionApplicantRepository;
import com.eduhub.edu_hub.admission.domain.repository.StudentAdmissionRepository;
import com.eduhub.edu_hub.notification.application.command.AdmissionNotificationCommand;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.AdmissionStudentRegistration;
import com.eduhub.edu_hub.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ApplicantEnrollmentService {
    private final AdmissionApplicantRepository applicants;
    private final StudentAdmissionRepository admissions;
    private final NotificationFacade.Queue notifications;
    private final PeopleFacade.Admission people;
    private final AdmissionIdentityPolicy identityPolicy;
    private final AdmissionProcessingLease lease;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enroll(Long planId, String token, Long applicantId) {
        var plan = lease.require(planId, token);
        var row = applicants.findById(applicantId).orElseThrow();
        if (!row.getPlan().getId().equals(planId)) throw new BaseException(ErrorCode.VALIDATION_ERROR);
        if (!row.isPending()) return;
        var existing = admissions.findByApplicationCode(row.getApplicationCode());
        if (existing.isPresent()) {
            if (!identityPolicy.same(row, existing.get()))
                throw new BaseException(ErrorCode.ADMISSION_APPLICATION_CONFLICT);
            row.setStudentId(existing.get().getStudentId());
            row.setReuseAccount(true);
            row.setState(AdmissionApplicantStatus.EXISTING);
            row.setErrorMessage("Hồ sơ đã tồn tại; không cấp thêm MSSV hoặc tài khoản");
            lease.renew(plan);
            return;
        }
        var registration = people.registerStudent(AdmissionStudentRegistration.builder()
                .classId(row.getAdministrativeClassId())
                .citizenId(row.getCitizenId())
                .fullName(row.getFullName())
                .dateOfBirth(row.getDateOfBirth())
                .gender(row.getGender())
                .email(row.getEmail())
                .phone(row.getPhone())
                .entranceScore(row.getEntranceScore())
                .build());
        Long studentId = registration.student().getId();
        if (registration.accountLocked())
            row.setWarningMessage("Tài khoản cũ đang khóa/ngừng hoạt động; giữ nguyên trạng thái");
        var admission = StudentAdmission.builder()
                .applicationCode(row.getApplicationCode())
                .studentId(studentId)
                .plan(plan)
                .administrativeClassId(row.getAdministrativeClassId())
                .citizenId(row.getCitizenId())
                .fullName(row.getFullName())
                .dateOfBirth(row.getDateOfBirth())
                .gender(row.getGender())
                .email(row.getEmail())
                .phone(row.getPhone())
                .entranceScore(row.getEntranceScore())
                .build();
        admissions.saveAndFlush(admission);
        notifications.queueAdmission(AdmissionNotificationCommand.builder()
                .admissionId(admission.getId())
                .studentId(studentId)
                .recipient(row.getEmail())
                .reEnrollment(registration.reused())
                .build());
        row.setStudentId(studentId);
        row.setReuseAccount(registration.reused());
        row.setState(AdmissionApplicantStatus.ENROLLED);
        row.setErrorMessage(null);
        lease.renew(plan);
        // TODO(AUDIT_LOG): Ghi kết quả từng hồ sơ và việc dùng lại danh tính sau khi commit.
        // Hàng đợi thuộc notification nhưng tham gia cùng transaction; listener chỉ gửi sau commit.
    }
}
