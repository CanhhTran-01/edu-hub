package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.UpdateMyStudentProfileRequest;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentProfileService {
    private final IdentityFacade identity;
    private final StudentRepository students;
    private final StudentApplicationService studentQueries;

    public StudentResponse get(String token) {

        var current = identity.requireCurrentUser(token);

        var student = students.findByUserIdAndDeletedFalse(current.getId())
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));

        return studentQueries.findById(student.getId());
    }

    @Transactional
    public StudentResponse update(String token, UpdateMyStudentProfileRequest request) {

        var current = identity.requireCurrentUser(token);

        var student = students.findMyProfileForUpdate(current.getId())
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
        boolean changed = false;
        if (request.hasTargetGpa()) {
            var value = request.getTargetGpa();
            if (value != null && (value.signum() < 0 || value.compareTo(java.math.BigDecimal.valueOf(4)) > 0))
                throw new BaseException(ErrorCode.INVALID_TARGET_GPA);
            boolean same = value == null
                    ? student.getTargetGpa() == null
                    : student.getTargetGpa() != null && value.compareTo(student.getTargetGpa()) == 0;
            if (!same) {
                changed = true;
                student.setTargetGpa(value);
            }
        }
        if (request.hasPhone()) {
            String phone =
                    request.getPhone() == null ? null : request.getPhone().trim();
            if (!Objects.equals(phone, student.getPhone())) {
                changed = true;
                student.setPhone(phone);
            }
        }
        if (changed) {
            students.save(student);
            // TODO(AUDIT_LOG): Bổ sung lại nhật ký cập nhật hồ sơ cá nhân sinh viên sau.
        }
        return studentQueries.findById(student.getId());
    }
}
