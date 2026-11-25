package com.eduhub.edu_hub.enrollment.application.facade.impl;

import com.eduhub.edu_hub.enrollment.api.dto.response.ModuleClassStudentResponse;
import com.eduhub.edu_hub.enrollment.api.mapper.ModuleClassStudentResponseMapper;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
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

        var studentProfiles = peopleFacade.findRosterStudentsByIds(enrollments.stream()
                .map(enrollment -> enrollment.getStudentId())
                .collect(Collectors.toSet()));
        return ModuleClassStudentResponseMapper.toResponses(enrollments, studentProfiles);
    }
}
