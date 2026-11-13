package com.eduhub.edu_hub.scheduling.domain.model;

import com.eduhub.edu_hub.scheduling.domain.enums.LearningOutcome;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import java.util.*;

public record AcademicPlanningSnapshot(
        String source,
        boolean historyComplete,
        List<PlanningStudent> students,
        List<LearningAttempt> attempts,
        Integer firstSupportedAdmissionYear,
        Integer excludedStudentCount,
        String scopeNotice) {

    public AcademicPlanningSnapshot(
            String source, boolean historyComplete, List<PlanningStudent> students, List<LearningAttempt> attempts) {
        this(source, historyComplete, students, attempts, null, 0, null);
    }

    public AcademicPlanningSnapshot {
        Objects.requireNonNull(source);
        excludedStudentCount = excludedStudentCount == null ? 0 : excludedStudentCount;
        students = List.copyOf(students);
        attempts = List.copyOf(attempts);
    }

    public record PlanningStudent(
            long id, String studentCode, String curriculumCode, int admissionYear, ActivityStatus activityStatus) {}

    public record LearningAttempt(
            long studentId, String subjectCode, int academicYearStart, int semesterNumber, LearningOutcome status) {
        public int termIndex() {
            return academicYearStart * 2 + semesterNumber;
        }
    }

    public void validate() {
        if (source.isBlank()) throw new IllegalArgumentException("Missing planning source");
        Set<Long> studentIds = new HashSet<>();
        Set<String> studentCodes = new HashSet<>();
        for (PlanningStudent student : students) {
            if (student.id() <= 0
                    || !studentIds.add(student.id())
                    || student.studentCode() == null
                    || student.studentCode().isBlank()
                    || !studentCodes.add(student.studentCode())
                    || student.curriculumCode() == null
                    || student.curriculumCode().isBlank()
                    || student.admissionYear() < 1
                    || student.activityStatus() == null)
                throw new IllegalArgumentException("Invalid student planning data");
        }
        for (LearningAttempt attempt : attempts) {
            if (!studentIds.contains(attempt.studentId())
                    || attempt.subjectCode() == null
                    || attempt.subjectCode().isBlank()
                    || attempt.academicYearStart() < 1
                    || attempt.semesterNumber() < 1
                    || attempt.semesterNumber() > 2
                    || attempt.status() == null) throw new IllegalArgumentException("Invalid learning attempt");
        }
    }
}
