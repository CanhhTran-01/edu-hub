package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.api.dto.response.AccountProfileContactResponse;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {
    List<Student> findByCitizenIdIn(List<String> citizenIds);

    Optional<Student> findByCitizenIdAndDeletedFalse(String citizenId);

    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    Optional<Student> findByUserIdAndDeletedFalse(Long userId);

    @Query("select student.fullName from Student student where student.userId = :userId and student.deleted = false")
    Optional<String> findFullNameByUserId(@Param("userId") Long userId);

    @Query(
            "select new com.eduhub.edu_hub.people.api.dto.response.AccountProfileContactResponse(student.fullName, student.phone) "
                    + "from Student student where student.userId = :userId and student.deleted = false")
    Optional<AccountProfileContactResponse> findAccountProfileContactByUserId(@Param("userId") Long userId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.userId = :userId and s.deleted = false")
    Optional<Student> findMyProfileForUpdate(@Param("userId") Long userId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select student from Student student where student.id = :studentId and student.deleted = false")
    Optional<Student> findStudentProfileByIdForUpdate(@Param("studentId") Long studentId);

    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    Optional<Student> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    List<Student> findAllByDeletedFalseAndActivityStatus(ActivityStatus activityStatus);

    @Override
    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    Page<Student> findAll(@NonNull Specification<Student> specification, @NonNull Pageable pageable);

    boolean existsByCitizenIdAndDeletedFalse(String citizenId);

    boolean existsByCitizenIdAndIdNotAndDeletedFalse(String citizenId, Long studentId);

    boolean existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(String fullName, java.time.LocalDate dateOfBirth);

    boolean existsByStudentCode(String studentCode);

    long countByAdministrativeClass_IdAndDeletedFalse(Long classId);

    @Query("select student.studentCode from Student student where student.administrativeClass.id = :classId")
    List<String> findStudentCodesByAdministrativeClassId(@Param("classId") Long classId);
}
