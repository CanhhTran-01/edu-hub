package com.eduhub.edu_hub.enrollment.domain.repository;

import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    @Query(
            "select enrollment.semesterId from Enrollment enrollment "
                    + "where enrollment.id = :enrollmentId and enrollment.studentId = :studentId and enrollment.deleted = false")
    Optional<Long> findSemesterIdByIdAndStudentId(
            @Param("enrollmentId") Long enrollmentId, @Param("studentId") Long studentId);

    List<Enrollment> findAllByStudentIdOrderByRegisteredAtAscIdAsc(Long studentId);

    List<Enrollment> findAllByStudentIdInOrderByIdAsc(Set<Long> studentIds);

    List<Enrollment> findAllByModuleClassIdOrderByIdAsc(Long moduleClassId);

    Optional<Enrollment> findByIdAndStudentIdAndDeletedFalse(Long enrollmentId, Long studentId);

    long countByModuleClassIdAndStatusAndDeletedFalse(Long moduleClassId, EnrollmentStatus status);
}
