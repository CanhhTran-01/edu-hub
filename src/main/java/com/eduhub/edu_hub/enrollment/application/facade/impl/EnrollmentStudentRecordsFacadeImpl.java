package com.eduhub.edu_hub.enrollment.application.facade.impl;

import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse.LearningRecordResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.policy.EnrollmentRegistrationPolicy;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.enrollment.infrastructure.config.EnrollmentRegistrationSettings;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentStudentRecordsFacadeImpl implements EnrollmentFacade.StudentRecords {
    private final EnrollmentRepository enrollmentRepository;
    private final PeopleFacade.Enrollment peopleFacade;
    private final EnrollmentRegistrationSettings settings;
    private final Clock clock;

    @Override
    public StudentLearningHistoryResponse findLearningHistory(Long studentId) {
        var currentBusinessTime = LocalDateTime.now(clock);
        var history = enrollmentRepository.findAllByStudentIdOrderByRegisteredAtAscIdAsc(studentId);
        Integer admissionYear =
                peopleFacade.findAdmissionYearsByStudentIds(Set.of(studentId)).get(studentId);
        boolean historyComplete = admissionYear != null && admissionYear >= settings.getFirstSupportedAdmissionYear();
        // Giữ cùng quy tắc tiên quyết với đăng ký: kết quả đạt lịch sử không mất chỉ vì lượt học bị ẩn.
        // TODO(ENROLLMENT_LEGACY_HISTORY): Chỉ xác nhận đủ lịch sử sau khi khóa cũ đã được đối soát/chuyển dữ liệu.
        return new StudentLearningHistoryResponse(
                historyComplete,
                settings.getFirstSupportedAdmissionYear(),
                settings.getScopeNotice(),
                EnrollmentRegistrationPolicy.passedSubjectIds(history),
                history.stream()
                        .filter(enrollment ->
                                !enrollment.isDeleted() && enrollment.getStatus() != EnrollmentStatus.DROPPED)
                        .map(enrollment -> new LearningRecordResponse(
                                enrollment.getId(),
                                enrollment.getModuleClassId(),
                                enrollment.getModuleClassCode(),
                                enrollment.getSubjectId(),
                                enrollment.getSubjectCode(),
                                enrollment.getSubjectName(),
                                enrollment.getSemesterId(),
                                enrollment.getAcademicYearStart(),
                                enrollment.getSemesterNumber(),
                                enrollment.getCredits(),
                                enrollment.getRegisteredAt(),
                                enrollment.getStatus(),
                                enrollment.getComponentScore1(),
                                enrollment.getComponentScore2(),
                                enrollment.getProcessScore(),
                                enrollment.getFinalExamScore(),
                                enrollment.getCourseScore10(),
                                enrollment.getCourseScoreLetter(),
                                enrollment.getCourseScore4(),
                                enrollment.getGradePublishedAt(),
                                enrollment.getGradeLockAt(),
                                enrollment.isGradeLocked(currentBusinessTime)))
                        .toList());
    }
}
