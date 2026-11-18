package com.eduhub.edu_hub.people.application.facade.impl;

import com.eduhub.edu_hub.people.api.dto.response.EnrollmentStudentResponse;
import com.eduhub.edu_hub.people.api.dto.response.StudentRosterProfileResponse;
import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeopleEnrollmentFacadeImpl implements PeopleFacade.Enrollment {
    private final StudentRepository studentRepository;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    public List<StudentRosterProfileResponse> findRosterStudentsByIds(Set<Long> studentIds) {
        // Chỉ đọc ba trường cần hiển thị; chia lô để không tạo một mệnh đề IN quá lớn.
        var orderedStudentIds = studentIds.stream().sorted().toList();
        var profiles = new ArrayList<StudentRosterProfileResponse>();
        final int profileBatchSize = 500;
        for (int startIndex = 0; startIndex < orderedStudentIds.size(); startIndex += profileBatchSize) {
            profiles.addAll(studentRepository.findRosterProfilesByIds(orderedStudentIds.subList(
                    startIndex, Math.min(startIndex + profileBatchSize, orderedStudentIds.size()))));
        }
        return profiles;
    }

    @Override
    public EnrollmentStudentResponse findStudentByUserId(Long userId) {
        return toResponse(studentRepository
                .findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new StudentProfileException(ErrorCode.STUDENT_NOT_FOUND)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EnrollmentStudentResponse lockStudentByUserId(Long userId) {
        // Khóa hồ sơ gốc để tuần tự hóa hai yêu cầu đăng ký khác lớp của cùng một sinh viên.
        var student = studentRepository
                .findMyProfileForUpdate(userId)
                .orElseThrow(() -> new StudentProfileException(ErrorCode.STUDENT_NOT_FOUND));
        // Nhánh hủy có thể đã đọc hồ sơ để kiểm tra ownership; đọc lại trạng thái sau khi chờ khóa.
        entityManager.refresh(student, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (student.isDeleted()) throw new StudentProfileException(ErrorCode.STUDENT_NOT_FOUND);
        return toResponse(student);
    }

    @Override
    public Map<Long, Integer> findAdmissionYearsByStudentIds(Set<Long> studentIds) {
        Map<Long, Integer> admissionYearsByStudentId = new HashMap<>();
        if (studentIds.isEmpty()) return admissionYearsByStudentId;
        for (Student student : studentRepository.findAllById(studentIds)) {
            var profile = toResponse(student);
            if (profile.admissionYear() != null)
                admissionYearsByStudentId.put(student.getId(), profile.admissionYear());
        }
        return admissionYearsByStudentId;
    }

    private EnrollmentStudentResponse toResponse(Student student) {
        var administrativeClass = student.getAdministrativeClass();
        var cohort =
                administrativeClass == null || administrativeClass.isDeleted() ? null : administrativeClass.getCohort();
        var major =
                administrativeClass == null || administrativeClass.isDeleted() ? null : administrativeClass.getMajor();
        return new EnrollmentStudentResponse(
                student.getId(),
                student.getStudentCode(),
                student.getFullName(),
                major == null || major.isDeleted() ? null : major.getId(),
                cohort == null || cohort.isDeleted() ? null : cohort.getId(),
                administrativeClass == null || administrativeClass.isDeleted()
                        ? null
                        : administrativeClass.getTrainingType(),
                cohort == null || cohort.isDeleted() ? null : cohort.getStartYear(),
                student.getActivityStatus());
    }
}
