package com.eduhub.edu_hub.enrollment.application.facade.impl;

import com.eduhub.edu_hub.enrollment.api.dto.response.*;
import com.eduhub.edu_hub.enrollment.api.dto.response.EnrollmentScopeResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.enrollment.infrastructure.config.EnrollmentRegistrationSettings;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentPlanningFacadeImpl implements EnrollmentFacade.Planning, EnrollmentFacade.RegistrationUsage {
    private final EnrollmentRepository enrollmentRepository;
    private final PeopleFacade.Enrollment peopleFacade;
    private final EnrollmentRegistrationSettings settings;

    @Override
    public EnrollmentScopeResponse findScope() {
        return new EnrollmentScopeResponse(settings.getFirstSupportedAdmissionYear(), settings.getScopeNotice());
    }

    @Override
    public LearningHistoryResponse findLearningHistory(Set<Long> studentIds) {
        var admissionYears = peopleFacade.findAdmissionYearsByStudentIds(studentIds);
        boolean complete = studentIds.stream()
                .allMatch(studentId -> admissionYears.containsKey(studentId)
                        && admissionYears.get(studentId) >= settings.getFirstSupportedAdmissionYear());
        // TODO(ENROLLMENT_LEGACY_HISTORY): Chỉ xác nhận đủ lịch sử cho khóa dùng hệ thống từ đầu.
        // Khi chuyển khóa cũ, phải đối soát lượt học/điểm và quản lý độ đầy đủ trước khi mở rộng phạm vi.
        var attempts = studentIds.isEmpty()
                ? java.util.List.<LearningAttemptResponse>of()
                : enrollmentRepository.findAllByStudentIdInOrderByIdAsc(studentIds).stream()
                        .map(enrollment -> new LearningAttemptResponse(
                                enrollment.getStudentId(),
                                enrollment.getSubjectCode(),
                                enrollment.getAcademicYearStart(),
                                enrollment.getSemesterNumber(),
                                enrollment.getStatus().name()))
                        .toList();
        return new LearningHistoryResponse(complete, attempts);
    }

    @Override
    public ModuleClassUsageResponse summarizeModuleClass(Long moduleClassId) {
        var enrollments = enrollmentRepository.findAllByModuleClassIdOrderByIdAsc(moduleClassId);
        return new ModuleClassUsageResponse(
                true,
                enrollments.size(),
                enrollments.stream()
                        .map(enrollment -> enrollment.getStudentId())
                        .distinct()
                        .count(),
                "DATABASE");
    }

    @Override
    public long countActiveRegistrations(Long moduleClassId) {
        return enrollmentRepository.countByModuleClassIdAndStatusAndDeletedFalse(
                moduleClassId, EnrollmentStatus.REGISTERED);
    }
}
