package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.people.api.dto.AdministrativeClassResponse;
import com.eduhub.edu_hub.people.api.dto.LecturerResponse;
import com.eduhub.edu_hub.people.api.dto.StudentResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PeopleFacade {
    // ===== Student =====
    Optional<StudentResponse> findStudentById(Long studentId);

    Optional<StudentResponse> findStudentByUserId(Long userId);

    Optional<StudentResponse> findStudentByCode(String studentCode);

    List<StudentResponse> findStudentsByClassId(Long classId);

    boolean existsStudent(Long studentId);

    /** Cập nhật GPA mục tiêu (dùng cho AI) */
    void updateTargetGpa(Long studentId, BigDecimal targetGpa);

    // ===== Lecturer =====
    Optional<LecturerResponse> findLecturerById(Long lecturerId);

    Optional<LecturerResponse> findLecturerByUserId(Long userId);

    Optional<LecturerResponse> findLecturerByCode(String lecturerCode);

    boolean existsLecturer(Long lecturerId);

    // ===== Organization =====
    Optional<AdministrativeClassResponse> findClassById(Long classId);

    List<AdministrativeClassResponse> findClassesByMajorAndCohort(Long majorId, Long cohortId);
}
