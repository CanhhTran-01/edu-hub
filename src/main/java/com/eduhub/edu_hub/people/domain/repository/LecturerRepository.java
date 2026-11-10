package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.Lecturer;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LecturerRepository extends JpaRepository<Lecturer, Long>, JpaSpecificationExecutor<Lecturer> {
    @Query(
            "select lecturer.fullName from Lecturer lecturer where lecturer.userId = :userId and lecturer.deleted = false")
    Optional<String> findFullNameByUserId(@Param("userId") Long userId);

    boolean existsByUserIdInAndDeletedFalse(Collection<Long> userIds);

    boolean existsByUserIdInAndIdNotAndDeletedFalse(Collection<Long> userIds, Long lecturerId);

    boolean existsByCitizenIdAndDeletedFalse(String citizenId);

    boolean existsByCitizenIdAndIdNotAndDeletedFalse(String citizenId, Long lecturerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lecturer from Lecturer lecturer where lecturer.id = :lecturerId and lecturer.deleted = false")
    Optional<Lecturer> findLecturerProfileByIdForUpdate(@Param("lecturerId") Long lecturerId);

    boolean existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(String fullName, java.time.LocalDate dateOfBirth);

    @EntityGraph(attributePaths = "faculty")
    Optional<Lecturer> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = "faculty")
    List<Lecturer> findAllByIdInAndDeletedFalse(Collection<Long> ids);
}
