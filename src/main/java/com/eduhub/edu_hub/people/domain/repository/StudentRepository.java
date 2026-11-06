package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {
    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    Optional<Student> findByUserIdAndDeletedFalse(Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.userId = :userId and s.deleted = false")
    Optional<Student> findMyProfileForUpdate(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    Optional<Student> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    List<Student> findAllByDeletedFalseAndActivityStatus(ActivityStatus activityStatus);

    @Override
    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    Page<Student> findAll(Specification<Student> specification, Pageable pageable);

    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);

    boolean existsByCitizenIdAndDeletedFalse(String citizenId);

    boolean existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(String fullName, java.time.LocalDate dateOfBirth);

    boolean existsByStudentCode(String studentCode);

    long countByAdministrativeClass_IdAndDeletedFalse(Long classId);

    @Query("select student.studentCode from Student student where student.administrativeClass.id = :classId")
    List<String> findStudentCodesByAdministrativeClassId(@Param("classId") Long classId);
}
