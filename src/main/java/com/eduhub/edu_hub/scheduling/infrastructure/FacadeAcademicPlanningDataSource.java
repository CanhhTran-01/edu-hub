package com.eduhub.edu_hub.scheduling.infrastructure;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.StudentPlanningResponse;
import com.eduhub.edu_hub.scheduling.application.port.AcademicPlanningDataSource;
import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FacadeAcademicPlanningDataSource implements AcademicPlanningDataSource {
    private final PeopleFacade.Scheduling people;
    private final CurriculumFacade.ReferenceData curriculum;
    private final ObjectProvider<EnrollmentFacade.Planning> enrollment;

    @Override
    public PlanningSnapshot snapshot() {

        // lấy ta toàn bộ sinh viên cần học
        var students = people.findStudentsForPlanning();

        if (students.isEmpty()) {
            return new PlanningSnapshot("DATABASE", true, List.of(), List.of());
        }

        // validate nghiệp vụ cho toàn bộ sinh viên vừa lấy, có bị khuyết field nào không ?
        students.forEach(this::requireCompleteStudent);

        // Gom các ngành (majorId) và khóa (cohortId) trong DS sinh viên lại
        var majorIds = students.stream().map(StudentPlanningResponse::majorId).collect(Collectors.toSet());
        var cohortIds = students.stream().map(StudentPlanningResponse::cohortId).collect(Collectors.toSet());

        // Hỏi curriculum: “với ngành ngày, khóa này, chương trình đào tạo nào được áp dụng?”
        Map<AssignmentKey, String> assignments = new HashMap<>();
        for (var assignment : curriculum.findCurriculumAssignments(majorIds, cohortIds)) {
            var key = new AssignmentKey(assignment.majorId(), assignment.cohortId(), assignment.trainingType());
            if (assignment.curriculumCode() == null
                    || assignment.curriculumCode().isBlank()
                    || assignments.put(key, assignment.curriculumCode()) != null)
                throw new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE);
        }

        // Với mỗi SV, tra map với key là bộ 3 "majorId + cohorId + trainingType" để lấy curriculumCode tương ứng
        var planningStudents = students.stream()
                .map(student -> {
                    String program = assignments.get(
                            new AssignmentKey(student.majorId(), student.cohortId(), student.trainingType()));

                    if (program == null) throw new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE);

                    return new PlanningSnapshot.Student(
                            student.id(),
                            student.studentCode(),
                            program,
                            student.admissionYear(),
                            student.activityStatus());
                })
                .toList();

        // lấy lịch sử học của sinh viên
        var facade = enrollment.getIfAvailable();
        if (facade == null) throw new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE);

        var history = facade.findLearningHistory(
                students.stream().map(StudentPlanningResponse::id).collect(Collectors.toSet()));
        if (history == null || !history.historyComplete() || history.attempts() == null)
            throw new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE);

        try {
            var attempts = history.attempts().stream()
                    .map(attempt -> new PlanningSnapshot.Attempt(
                            attempt.studentId(),
                            attempt.subjectCode(),
                            attempt.academicYearStart(),
                            attempt.semesterNumber(),
                            PlanningSnapshot.Outcome.valueOf(attempt.status())))
                    .toList();

            var snapshot = new PlanningSnapshot("DATABASE", true, planningStudents, attempts);
            snapshot.validate();

            return snapshot;

        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE);
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

            throw new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE);
        }
    }

    private record AssignmentKey(Long majorId, Long cohortId, TrainingType trainingType) {}
}
