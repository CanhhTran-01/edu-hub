package com.eduhub.edu_hub.scheduling.application;

import com.eduhub.edu_hub.curriculum.application.facade.dto.CurriculumOpeningSubjectResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.OpeningSubjectResponse;
import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot;
import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot.Attempt;
import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot.Outcome;
import com.eduhub.edu_hub.scheduling.domain.policy.ModuleClassPolicy;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OpeningDemandCalculator {
    private static final Pattern SEMESTER_CODE = Pattern.compile("^HK_([12])_([0-9]{4})_([0-9]{4})$");

    public List<OpeningSubjectResponse> calculate(
            String semesterCode, List<CurriculumOpeningSubjectResponse> plan, PlanningSnapshot data) {

        // Valide mã kỳ
        var matcher = SEMESTER_CODE.matcher(semesterCode);
        if (!matcher.matches()) throw new BaseException(ErrorCode.PLANNING_SEMESTER_UNSUPPORTED);

        // kiểm tra: academicYear >= 1, Năm kết thúc = năm bắt đầu + 1, historyComplete = true.
        int semesterNumber = Integer.parseInt(matcher.group(1));
        int academicYear = Integer.parseInt(matcher.group(2));
        if (academicYear < 1 || Integer.parseInt(matcher.group(3)) != academicYear + 1)
            throw new BaseException(ErrorCode.PLANNING_SEMESTER_UNSUPPORTED);
        if (!data.historyComplete()) throw new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE);

        // Quy kỳ cần mở thành một số duy nhất
        int targetTerm = academicYear * 2 + semesterNumber;

        // gom dữ liệu thành Map<mã CTĐT, List<môn trong CTĐT>> làm programs
        var programs =
                plan.stream().collect(Collectors.groupingBy(CurriculumOpeningSubjectResponse::getCurriculumCode));

        // Lịch sử học gom thành Map<studentId, Map<subjectCode, List<Attempt>>> làm history
        // chỉ lấy các lần học không sau kỳ đang tính (<= targetTerm), để tra nhanh “SV X đã học môn Y thế nào”
        var history = data.attempts().stream()
                .filter(attempt -> attempt.termIndex() <= targetTerm)
                .collect(Collectors.groupingBy(Attempt::studentId, Collectors.groupingBy(Attempt::subjectCode)));

        // bắt đầu tính toán
        Map<Long, Demand> demands = new HashMap<>();
        for (var student : data.students()) {

            // Tính SV đang ở học kỳ thứ mấy của chương trình
            // Ví dụ nhập học 2023, mở kỳ 1 năm 2026: 2*(2026-2024)+1 = 7 (kỳ thứ 7)
            int currentSemester = 2 * (academicYear - student.admissionYear()) + semesterNumber;

            // Bỏ qua SV không STUDYING hoặc currentSemester < 1 (chưa nhập học)
            if (student.activityStatus() != ActivityStatus.STUDYING || currentSemester < 1) continue;

            // Không tìm thấy chương trình của SV thì lỗi
            var subjects = programs.get(student.curriculumCode());
            if (subjects == null) throw new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE);

            // với mỗi sinh viên, ta lấy ra lịch sử học gồm subject + list attempts
            // "COMPLETED, REGISTER" thì bỏ qua, "ON_TIME, MISSED, RETAKE" cần xử lý
            var studentHistory = history.getOrDefault(student.id(), Map.of());
            for (var subject : subjects) {

                // Lấy attempts tương ứng
                List<Attempt> attempts = studentHistory.getOrDefault(subject.getSubjectCode(), List.of());

                // Đã đạt thì không thuộc nhu cầu bắt buộc
                if (attempts.stream()
                        .anyMatch(attempt ->
                                attempt.status() == Outcome.COMPLETED || attempt.status() == Outcome.REGISTERED))
                    continue;

                // TODO: học cải thiện (với status COMPLETED) do enrollment xử lý sau.

                // phân nhóm cho nhu cầu bắt buộc
                Group group = null;
                if (subject.getRecommendSemester() == currentSemester) group = Group.ON_TIME;
                else if (attempts.stream().anyMatch(attempt -> attempt.status() == Outcome.FAILED))
                    group = Group.RETAKE;
                else if (subject.getRecommendSemester() < currentSemester && attempts.isEmpty()) group = Group.MISSED;

                if (group != null)
                    demands.computeIfAbsent(subject.getSubjectId(), ignored -> new Demand(subject))
                            .students
                            .putIfAbsent(student.id(), group);
            }
        }

        // trả lại dữ liệu đã xử lý
        return demands.values().stream()
                .map(demand -> demand.toResponse(data))
                .sorted(Comparator.comparing(OpeningSubjectResponse::getSubjectCode))
                .toList();
    }

    private enum Group {
        ON_TIME,
        RETAKE,
        MISSED
    }

    private static final class Demand {
        private final CurriculumOpeningSubjectResponse subject;
        private final Map<Long, Group> students = new HashMap<>();

        private Demand(CurriculumOpeningSubjectResponse subject) {
            this.subject = subject;
        }

        // xử lý dữ liệu nhu cầu cho subject
        private OpeningSubjectResponse toResponse(PlanningSnapshot data) {
            return OpeningSubjectResponse.builder()
                    .subjectId(subject.getSubjectId())
                    .subjectCode(subject.getSubjectCode())
                    .subjectName(subject.getSubjectName())
                    .credits(subject.getCredits())
                    .onTimeStudentCount(count(Group.ON_TIME))
                    .retakeStudentCount(count(Group.RETAKE))
                    .missedStudentCount(count(Group.MISSED))
                    .expectedStudentCount(students.size())
                    .recommendedClassCount(
                            (students.size() + ModuleClassPolicy.MAX_CAPACITY - 1) / ModuleClassPolicy.MAX_CAPACITY)
                    .dataSource(data.source())
                    .historyComplete(data.historyComplete())
                    .build();
        }

        private int count(Group group) {
            return (int)
                    students.values().stream().filter(value -> value == group).count();
        }
    }
}
