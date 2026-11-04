package com.eduhub.edu_hub.scheduling.application.port;

import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import java.util.*;

public record PlanningSnapshot(String source, boolean historyComplete, List<Student> students, List<Attempt> attempts) {
    public PlanningSnapshot {
        Objects.requireNonNull(source);
        students = List.copyOf(students);
        attempts = List.copyOf(attempts);
    }

    /** COMPLETED trong contract lập kế hoạch nghĩa là đã đạt, không chỉ có điểm thành phần. */
    public enum Outcome {
        REGISTERED,
        DROPPED,
        COMPLETED,
        FAILED
    }

    public record Student(
            long id, String studentCode, String curriculumCode, int admissionYear, ActivityStatus activityStatus) {}

    public record Attempt(
            long studentId, String subjectCode, int academicYearStart, int semesterNumber, Outcome status) {
        public int termIndex() {
            return academicYearStart * 2 + semesterNumber;
        }
    }

    public void validate() {
        if (source.isBlank()) throw new IllegalArgumentException("Missing planning source");
        Set<Long> studentIds = new HashSet<>();
        Set<String> studentCodes = new HashSet<>();
        for (Student student : students) {
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
        for (Attempt attempt : attempts) {
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
