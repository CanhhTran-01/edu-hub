package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.identity.application.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.StudentResponse;
import com.eduhub.edu_hub.people.api.dto.UpdateMyStudentProfileRequest;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.audit.AuditLog;
import com.eduhub.edu_hub.shared.audit.AuditLogRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyStudentProfileService {
    private final IdentityFacade identity;
    private final StudentRepository students;
    private final StudentApplicationService studentQueries;
    private final AuditLogRepository audits;

    public StudentResponse get(String token) {
        var current = identity.requireCurrentStudent(token);
        var student = students.findByUserIdAndDeletedFalse(current.getId())
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
        return studentQueries.findById(student.getId());
    }

    @Transactional
    public StudentResponse update(String token, UpdateMyStudentProfileRequest request) {
        var current = identity.requireCurrentStudent(token);
        var student = students.findMyProfileForUpdate(current.getId())
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
        var changes = new ArrayList<String>();
        if (request.hasTargetGpa()) {
            var value = request.getTargetGpa();
            if (value != null && (value.signum() < 0 || value.compareTo(java.math.BigDecimal.valueOf(4)) > 0))
                throw new BaseException(ErrorCode.INVALID_TARGET_GPA);
            boolean same = value == null ? student.getTargetGpa() == null
                    : student.getTargetGpa() != null && value.compareTo(student.getTargetGpa()) == 0;
            if (!same) {
                changes.add("targetGpa: " + student.getTargetGpa() + " -> " + value);
                student.setTargetGpa(value);
            }
        }
        if (request.hasPhone()) {
            String phone = request.getPhone() == null ? null : request.getPhone().trim();
            if (!Objects.equals(phone, student.getPhone())) {
                changes.add("phone: " + student.getPhone() + " -> " + phone);
                student.setPhone(phone);
            }
        }
        if (!changes.isEmpty()) {
            students.save(student);
            AuditLog audit = new AuditLog();
            audit.setActor(current.getUsername()); audit.setAction("UPDATE_MY_STUDENT_PROFILE");
            audit.setEntityType("STUDENT"); audit.setEntityId(student.getId().toString());
            audit.setDetails(String.join("; ", changes)); audits.save(audit);
        }
        return studentQueries.findById(student.getId());
    }
}
