package com.eduhub.edu_hub.scheduling.domain.policy;

import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassException;
import com.eduhub.edu_hub.scheduling.domain.enums.LearningOutcome;
import com.eduhub.edu_hub.scheduling.domain.enums.OpeningDemandCategory;
import com.eduhub.edu_hub.scheduling.domain.model.AcademicPlanningSnapshot;
import com.eduhub.edu_hub.scheduling.domain.model.AcademicPlanningSnapshot.LearningAttempt;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OpeningDemandPolicy {
    private static final Pattern SEMESTER_CODE = Pattern.compile("^HK_([12])_([0-9]{4})_([0-9]{4})$");

    public List<SubjectDemand> calculate(
            String semesterCode, List<PlanningSubject> planningSubjects, AcademicPlanningSnapshot planningSnapshot) {

        // Valide mã kỳ
        var matcher = SEMESTER_CODE.matcher(semesterCode);
        if (!matcher.matches()) throw new ModuleClassException(ErrorCode.PLANNING_SEMESTER_UNSUPPORTED);

        // kiểm tra: academicYear >= 1, Năm kết thúc = năm bắt đầu + 1, historyComplete = true.
        int semesterNumber = Integer.parseInt(matcher.group(1));
        int academicYear = Integer.parseInt(matcher.group(2));
        if (academicYear < 1 || Integer.parseInt(matcher.group(3)) != academicYear + 1)
            throw new ModuleClassException(ErrorCode.PLANNING_SEMESTER_UNSUPPORTED);
        if (!planningSnapshot.historyComplete()) throw new ModuleClassException(ErrorCode.PLANNING_DATA_UNAVAILABLE);

        // Quy kỳ cần mở thành một số duy nhất
        int targetTerm = academicYear * 2 + semesterNumber;

        // gom dữ liệu thành Map<mã CTĐT, List<môn trong CTĐT>> làm programs
        var subjectsByCurriculumCode =
                planningSubjects.stream().collect(Collectors.groupingBy(PlanningSubject::curriculumCode));

        // Lịch sử học gom thành Map<studentId, Map<subjectCode, List<Attempt>>> làm history
        // chỉ lấy các lần học không sau kỳ đang tính (<= targetTerm), để tra nhanh “SV X đã học môn Y thế nào”
        var learningHistoryByStudentId = planningSnapshot.attempts().stream()
                .filter(attempt -> attempt.termIndex() <= targetTerm)
                .collect(Collectors.groupingBy(
                        LearningAttempt::studentId, Collectors.groupingBy(LearningAttempt::subjectCode)));

        // bắt đầu tính toán
        Map<Long, StudentDemandBySubject> demandsBySubjectId = new HashMap<>();
        for (var student : planningSnapshot.students()) {

            // Tính SV đang ở học kỳ thứ mấy của chương trình
            // Ví dụ nhập học 2023, mở kỳ 1 năm 2026: 2*(2026-2024)+1 = 7 (kỳ thứ 7)
            int currentSemester = 2 * (academicYear - student.admissionYear()) + semesterNumber;

            // Bỏ qua SV không STUDYING hoặc currentSemester < 1 (chưa nhập học)
            if (student.activityStatus() != ActivityStatus.STUDYING || currentSemester < 1) continue;

            // Không tìm thấy chương trình của SV thì lỗi
            var curriculumSubjects = subjectsByCurriculumCode.get(student.curriculumCode());
            if (curriculumSubjects == null) throw new ModuleClassException(ErrorCode.PLANNING_DATA_UNAVAILABLE);

            // với mỗi sinh viên, ta lấy ra lịch sử học gồm subject + list attempts
            // "COMPLETED, REGISTER" thì bỏ qua, "ON_TIME, MISSED, RETAKE" cần xử lý
            var subjectHistory = learningHistoryByStudentId.getOrDefault(student.id(), Map.of());
            for (var planningSubject : curriculumSubjects) {

                // Lấy attempts tương ứng
                List<LearningAttempt> learningAttempts =
                        subjectHistory.getOrDefault(planningSubject.subjectCode(), List.of());

                // Đã đạt thì không thuộc nhu cầu bắt buộc
                if (learningAttempts.stream()
                        .anyMatch(attempt -> attempt.status() == LearningOutcome.COMPLETED
                                || attempt.status() == LearningOutcome.REGISTERED)) continue;

                // TODO: học cải thiện (với status COMPLETED) do enrollment xử lý sau.

                // phân nhóm cho nhu cầu bắt buộc
                OpeningDemandCategory demandCategory = null;
                if (planningSubject.recommendSemester() == currentSemester)
                    demandCategory = OpeningDemandCategory.ON_TIME;
                else if (learningAttempts.stream().anyMatch(attempt -> attempt.status() == LearningOutcome.FAILED))
                    demandCategory = OpeningDemandCategory.RETAKE;
                else if (planningSubject.recommendSemester() < currentSemester && learningAttempts.isEmpty())
                    demandCategory = OpeningDemandCategory.MISSED;

                if (demandCategory != null)
                    demandsBySubjectId
                            .computeIfAbsent(
                                    planningSubject.subjectId(), ignored -> new StudentDemandBySubject(planningSubject))
                            .studentsById
                            .putIfAbsent(student.id(), demandCategory);
            }
        }

        // trả lại dữ liệu đã xử lý
        return demandsBySubjectId.values().stream()
                .map(StudentDemandBySubject::summarizeDemand)
                .sorted(Comparator.comparing(demand -> demand.subject().subjectCode()))
                .toList();
    }

    private static final class StudentDemandBySubject {
        private final PlanningSubject planningSubject;
        private final Map<Long, OpeningDemandCategory> studentsById = new HashMap<>();

        private StudentDemandBySubject(PlanningSubject planningSubject) {
            this.planningSubject = planningSubject;
        }

        // xử lý dữ liệu nhu cầu cho subject
        private SubjectDemand summarizeDemand() {
            return new SubjectDemand(
                    planningSubject,
                    count(OpeningDemandCategory.ON_TIME),
                    count(OpeningDemandCategory.RETAKE),
                    count(OpeningDemandCategory.MISSED),
                    studentsById.size(),
                    (studentsById.size() + ModuleClassPolicy.MAX_CAPACITY - 1) / ModuleClassPolicy.MAX_CAPACITY);
        }

        private int count(OpeningDemandCategory demandCategory) {
            return (int) studentsById.values().stream()
                    .filter(studentDemandCategory -> studentDemandCategory == demandCategory)
                    .count();
        }
    }

    public record PlanningSubject(
            String curriculumCode,
            Long subjectId,
            String subjectCode,
            String subjectName,
            int credits,
            int recommendSemester) {}

    public record SubjectDemand(
            PlanningSubject subject,
            int onTimeStudentCount,
            int retakeStudentCount,
            int missedStudentCount,
            int expectedStudentCount,
            int recommendedClassCount) {}
}
