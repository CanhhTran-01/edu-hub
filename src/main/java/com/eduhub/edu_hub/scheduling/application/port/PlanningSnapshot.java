package com.eduhub.edu_hub.scheduling.application.port;

import java.util.*;

public record PlanningSnapshot(
        String source,
        boolean historyComplete,
        List<User> users,
        List<Student> students,
        List<Lecturer> lecturers,
        List<Attempt> attempts) {
    public PlanningSnapshot {
        Objects.requireNonNull(source);
        users = List.copyOf(users);
        students = List.copyOf(students);
        lecturers = List.copyOf(lecturers);
        attempts = List.copyOf(attempts);
    }

    public enum Role {
        STUDENT,
        LECTURER
    }
    /** COMPLETED trong contract lập kế hoạch nghĩa là đã đạt, không chỉ có điểm thành phần. */
    public enum Outcome {
        REGISTERED,
        DROPPED,
        COMPLETED,
        FAILED
    }

    public record User(long id, String fullName, Role role, boolean active) {}

    public record Student(
            long id, long userId, String studentCode, String curriculumCode, int admissionYear, boolean active) {}

    public record Lecturer(long id, long userId, String lecturerCode, boolean active) {}

    public record Attempt(
            long studentId, String subjectCode, int academicYearStart, int semesterNumber, Outcome status) {
        public int termIndex() {
            return academicYearStart * 2 + semesterNumber;
        }
    }

    public Map<Long, User> usersById() {
        Map<Long, User> result = new HashMap<>();
        users.forEach(user -> result.put(user.id(), user));
        return result;
    }

    public void validate() {
        if (source.isBlank()) throw new IllegalArgumentException("Missing planning source");

        Map<Long, User> byId = new HashMap<>();
        for (User user : users) {
            if (user.id() <= 0
                    || user.fullName() == null
                    || user.fullName().isBlank()
                    || user.role() == null
                    || byId.put(user.id(), user) != null)
                throw new IllegalArgumentException("Invalid or duplicate fixture user");
        }

        Set<Long> profileUsers = new HashSet<>();
        Set<Long> studentIds = new HashSet<>();
        Set<Long> lecturerIds = new HashSet<>();
        Set<String> studentCodes = new HashSet<>();
        Set<String> lecturerCodes = new HashSet<>();

        for (Student student : students) {
            User user = byId.get(student.userId());
            if (student.id() <= 0
                    || !studentIds.add(student.id())
                    || user == null
                    || user.role() != Role.STUDENT
                    || !profileUsers.add(student.userId())
                    || student.studentCode() == null
                    || student.studentCode().isBlank()
                    || !studentCodes.add(student.studentCode())
                    || student.curriculumCode() == null
                    || student.curriculumCode().isBlank()
                    || student.admissionYear() < 1)
                throw new IllegalArgumentException("Invalid Student/User fixture mapping");
        }

        for (Lecturer lecturer : lecturers) {
            User user = byId.get(lecturer.userId());
            if (lecturer.id() <= 0
                    || !lecturerIds.add(lecturer.id())
                    || user == null
                    || user.role() != Role.LECTURER
                    || !profileUsers.add(lecturer.userId())
                    || lecturer.lecturerCode() == null
                    || lecturer.lecturerCode().isBlank()
                    || !lecturerCodes.add(lecturer.lecturerCode()))
                throw new IllegalArgumentException("Invalid Lecturer/User fixture mapping");
        }

        for (Attempt attempt : attempts) {
            if (!studentIds.contains(attempt.studentId())
                    || attempt.subjectCode() == null
                    || attempt.subjectCode().isBlank()
                    || attempt.academicYearStart() < 1
                    || attempt.semesterNumber() < 1
                    || attempt.semesterNumber() > 2
                    || attempt.status() == null) throw new IllegalArgumentException("Invalid learning attempt fixture");
        }
    }
}
