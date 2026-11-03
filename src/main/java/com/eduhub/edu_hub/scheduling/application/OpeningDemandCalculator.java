package com.eduhub.edu_hub.scheduling.application;

import com.eduhub.edu_hub.curriculum.api.dto.CurriculumOpeningSubjectResponse;
import com.eduhub.edu_hub.scheduling.api.dto.OpeningSubjectResponse;
import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot;
import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot.Attempt;
import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot.Outcome;
import com.eduhub.edu_hub.scheduling.domain.policy.ModuleClassPolicy;
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

        var matcher = SEMESTER_CODE.matcher(semesterCode);
        if (!matcher.matches()) throw new BaseException(ErrorCode.PLANNING_SEMESTER_UNSUPPORTED);

        int semesterNumber = Integer.parseInt(matcher.group(1));
        int academicYear = Integer.parseInt(matcher.group(2));
        if (academicYear < 1 || Integer.parseInt(matcher.group(3)) != academicYear + 1)
            throw new BaseException(ErrorCode.PLANNING_SEMESTER_UNSUPPORTED);

        if (!data.historyComplete()) throw new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE);

        int targetTerm = academicYear * 2 + semesterNumber;
        var users = data.usersById();
        var programs =
                plan.stream().collect(Collectors.groupingBy(CurriculumOpeningSubjectResponse::getCurriculumCode));

        var history = data.attempts().stream()
                .filter(attempt -> attempt.termIndex() <= targetTerm)
                .collect(Collectors.groupingBy(Attempt::studentId, Collectors.groupingBy(Attempt::subjectCode)));

        Map<Long, Demand> demands = new HashMap<>();
        for (var student : data.students()) {
            var user = users.get(student.userId());
            int currentSemester = 2 * (academicYear - student.admissionYear()) + semesterNumber;

            if (!student.active() || user == null || !user.active() || currentSemester < 1) continue;

            var subjects = programs.get(student.curriculumCode());
            if (subjects == null) throw new BaseException(ErrorCode.PLANNING_DATA_UNAVAILABLE);

            var studentHistory = history.getOrDefault(student.id(), Map.of());

            for (var subject : subjects) {

                List<Attempt> attempts = studentHistory.getOrDefault(subject.getSubjectCode(), List.of());

                // Đã đạt thì không thuộc nhu cầu bắt buộc; học cải thiện do enrollment xử lý sau.
                if (attempts.stream()
                        .anyMatch(attempt ->
                                attempt.status() == Outcome.COMPLETED || attempt.status() == Outcome.REGISTERED))
                    continue;

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
