package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.Student;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {
    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    Optional<Student> findByIdAndDeletedFalse(Long id);

    boolean existsByAdmissionApplicationCodeAndDeletedFalse(String admissionApplicationCode);

    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);

    boolean existsByCitizenIdAndDeletedFalse(String citizenId);

    boolean existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(
            String fullName, java.time.LocalDate dateOfBirth);

    boolean existsByStudentCode(String studentCode);

    List<Student> findAllByAdministrativeClass_IdAndDeletedFalse(Long classId);

    long countByAdministrativeClass_IdAndDeletedFalse(Long classId);

    @Query("select student.studentCode from Student student where student.administrativeClass.id = :classId")
    List<String> findStudentCodesByAdministrativeClassId(@Param("classId") Long classId);
}
