package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdministrativeClassRepository extends JpaRepository<AdministrativeClass, Long> {
    @EntityGraph(attributePaths = "cohort")
    List<AdministrativeClass> findByMajor_IdAndCohort_CohortCodeIgnoreCaseAndTrainingTypeAndDeletedFalse(
            Long majorId, String cohortCode, TrainingType trainingType);

    @EntityGraph(attributePaths = "cohort")
    Optional<AdministrativeClass> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = "cohort")
    List<AdministrativeClass>
            findByMajor_MajorCodeIgnoreCaseAndCohort_CohortCodeIgnoreCaseAndTrainingTypeAndDeletedFalse(
                    String majorCode, String cohortCode, TrainingType trainingType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "cohort")
    @Query("select administrativeClass from AdministrativeClass administrativeClass "
            + "where administrativeClass.id = :id and administrativeClass.deleted = false")
    Optional<AdministrativeClass> findByIdForStudentCodeUpdate(@Param("id") Long id);
}
