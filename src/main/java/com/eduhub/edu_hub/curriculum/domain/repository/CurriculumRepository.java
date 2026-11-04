package com.eduhub.edu_hub.curriculum.domain.repository;

import com.eduhub.edu_hub.curriculum.domain.model.Curriculum;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {
    @EntityGraph(attributePaths = {"major", "cohort"})
    Optional<Curriculum> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"major", "cohort"})
    Optional<Curriculum> findByMajor_IdAndCohort_IdAndTrainingTypeAndDeletedFalse(
            Long majorId, Long cohortId, TrainingType trainingType);

    @EntityGraph(attributePaths = {"major", "cohort"})
    List<Curriculum> findAllByMajor_IdInAndCohort_IdInAndDeletedFalse(
            Collection<Long> majorIds, Collection<Long> cohortIds);
}
