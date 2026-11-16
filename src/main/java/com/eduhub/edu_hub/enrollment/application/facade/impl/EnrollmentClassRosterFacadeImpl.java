package com.eduhub.edu_hub.enrollment.application.facade.impl;

import com.eduhub.edu_hub.enrollment.api.dto.response.ModuleClassStudentResponse;
import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.people.api.dto.response.StudentRosterProfileResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentClassRosterFacadeImpl implements EnrollmentFacade.ClassRoster {
    private static final Set<EnrollmentStatus> ROSTER_STATUSES =
            Set.of(EnrollmentStatus.REGISTERED, EnrollmentStatus.COMPLETED, EnrollmentStatus.FAILED);

    private final EnrollmentRepository enrollmentRepository;
    private final PeopleFacade.Enrollment peopleFacade;

    @Override
    public List<ModuleClassStudentResponse> findStudentsByModuleClassId(Long moduleClassId) {
        // COMPLETED/FAILED vẫn thuộc danh sách lớp cũ; chỉ loại lượt hủy hoặc đã xóa mềm.
        var enrollments = enrollmentRepository.findAllByModuleClassIdAndDeletedFalseAndStatusInOrderByIdAsc(
                moduleClassId, ROSTER_STATUSES);
        if (enrollments.isEmpty()) return List.of();

        var studentsById = peopleFacade
                .findRosterStudentsByIds(enrollments.stream()
                        .map(enrollment -> enrollment.getStudentId())
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(StudentRosterProfileResponse::studentId, student -> student));

        return enrollments.stream()
                .map(enrollment -> {
                    var student = studentsById.get(enrollment.getStudentId());
                    // Không âm thầm bỏ sinh viên và xuất một danh sách thiếu khi liên kết hồ sơ bị lỗi.
                    if (student == null || student.studentCode() == null || student.fullName() == null)
                        throw new EnrollmentException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE);
                    return new ModuleClassStudentResponse(
                            enrollment.getId(),
                            student.studentId(),
                            student.studentCode(),
                            student.fullName(),
                            enrollment.getComponentScore1(),
                            enrollment.getComponentScore2(),
                            enrollment.getProcessScore(),
                            "");
                })
                .sorted(Comparator.comparing(ModuleClassStudentResponse::studentCode)
                        .thenComparing(ModuleClassStudentResponse::enrollmentId))
                .toList();
    }
}
