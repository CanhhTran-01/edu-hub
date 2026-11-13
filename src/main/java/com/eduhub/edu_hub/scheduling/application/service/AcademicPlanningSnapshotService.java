package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.StudentPlanningResponse;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassException;
import com.eduhub.edu_hub.scheduling.domain.enums.LearningOutcome;
import com.eduhub.edu_hub.scheduling.domain.model.AcademicPlanningSnapshot;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Tải bản chụp phục vụ tính nhu cầu mở lớp từ các facade sở hữu dữ liệu. */
@Service
@RequiredArgsConstructor
public class AcademicPlanningSnapshotService {
    private final PeopleFacade.Scheduling peopleFacade;
    private final CurriculumFacade.ReferenceData curriculumFacade;
    private final ObjectProvider<EnrollmentFacade.Planning> enrollmentFacadeProvider;

    /** DTO đầu vào từ facade People, Curriculum và lịch sử Enrollment. */
    public AcademicPlanningSnapshot loadPlanningSnapshot() {

        // lấy ta toàn bộ sinh viên cần học
        var students = peopleFacade.findStudentsForPlanning();

        if (students.isEmpty()) {
            return new AcademicPlanningSnapshot("DATABASE", true, List.of(), List.of());
        }

        // validate nghiệp vụ cho toàn bộ sinh viên vừa lấy, có bị khuyết field nào không ?
        students.forEach(this::requireCompleteStudent);

        var enrollmentFacade = enrollmentFacadeProvider.getIfAvailable();
        if (enrollmentFacade == null) throw new ModuleClassException(ErrorCode.PLANNING_DATA_UNAVAILABLE);
        var scope = enrollmentFacade.findScope();
        if (scope == null) throw new ModuleClassException(ErrorCode.PLANNING_DATA_UNAVAILABLE);
        int originalStudentCount = students.size();
        students = students.stream()
                .filter(student -> student.admissionYear() >= scope.firstSupportedAdmissionYear())
                .toList();
        int excludedStudentCount = originalStudentCount - students.size();
        if (students.isEmpty())
            return new AcademicPlanningSnapshot(
                    "DATABASE",
                    true,
                    List.of(),
                    List.of(),
                    scope.firstSupportedAdmissionYear(),
                    excludedStudentCount,
                    scope.notice());

        // TODO(ENROLLMENT_LEGACY_HISTORY): Chuyển và đối soát lịch sử khóa cũ trước khi mở rộng phạm vi thống kê.

        // Gom các ngành (majorId) và khóa (cohortId) trong DS sinh viên lại
        var majorIds = students.stream().map(StudentPlanningResponse::majorId).collect(Collectors.toSet());
        var cohortIds = students.stream().map(StudentPlanningResponse::cohortId).collect(Collectors.toSet());

        // Hỏi curriculum: “với ngành ngày, khóa này, chương trình đào tạo nào được áp dụng?”
        Map<AssignmentKey, String> curriculumCodesByAssignment = new HashMap<>();
        for (var assignment : curriculumFacade.findCurriculumAssignments(majorIds, cohortIds)) {
            var assignmentKey =
                    new AssignmentKey(assignment.majorId(), assignment.cohortId(), assignment.trainingType());
            if (assignment.curriculumCode() == null
                    || assignment.curriculumCode().isBlank()
                    || curriculumCodesByAssignment.put(assignmentKey, assignment.curriculumCode()) != null)
                throw new ModuleClassException(ErrorCode.PLANNING_DATA_UNAVAILABLE);
        }

        // Với mỗi SV, tra map với key là bộ 3 "majorId + cohorId + trainingType" để lấy curriculumCode tương ứng
        var planningStudents = students.stream()
                .map(student -> {
                    String curriculumCode = curriculumCodesByAssignment.get(
                            new AssignmentKey(student.majorId(), student.cohortId(), student.trainingType()));

                    if (curriculumCode == null) throw new ModuleClassException(ErrorCode.PLANNING_DATA_UNAVAILABLE);

                    return new AcademicPlanningSnapshot.PlanningStudent(
                            student.id(),
                            student.studentCode(),
                            curriculumCode,
                            student.admissionYear(),
                            student.activityStatus());
                })
                .toList();

        // lấy lịch sử học của sinh viên
        var learningHistory = enrollmentFacade.findLearningHistory(
                students.stream().map(StudentPlanningResponse::id).collect(Collectors.toSet()));
        if (learningHistory == null || !learningHistory.historyComplete() || learningHistory.attempts() == null)
            throw new ModuleClassException(ErrorCode.PLANNING_DATA_UNAVAILABLE);

        try {
            var learningAttempts = learningHistory.attempts().stream()
                    .map(attempt -> new AcademicPlanningSnapshot.LearningAttempt(
                            attempt.studentId(),
                            attempt.subjectCode(),
                            attempt.academicYearStart(),
                            attempt.semesterNumber(),
                            LearningOutcome.valueOf(attempt.status())))
                    .toList();

            var planningSnapshot = new AcademicPlanningSnapshot(
                    "DATABASE",
                    true,
                    planningStudents,
                    learningAttempts,
                    scope.firstSupportedAdmissionYear(),
                    excludedStudentCount,
                    scope.notice());
            planningSnapshot.validate();

            return planningSnapshot;

        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ModuleClassException(ErrorCode.PLANNING_DATA_UNAVAILABLE);
        }
    }

    private void requireCompleteStudent(StudentPlanningResponse student) {
        if (student.id() == null
                || student.id() <= 0
                || student.majorId() == null
                || student.majorId() <= 0
                || student.cohortId() == null
                || student.cohortId() <= 0
                || student.trainingType() == null
                || student.admissionYear() == null
                || student.admissionYear() < 1) {

            throw new ModuleClassException(ErrorCode.PLANNING_DATA_UNAVAILABLE);
        }
    }

    private record AssignmentKey(Long majorId, Long cohortId, TrainingType trainingType) {}
}
