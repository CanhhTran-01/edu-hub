package com.eduhub.edu_hub.curriculum.domain.repository;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.Curriculum;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {
    @EntityGraph(attributePaths = "major")
    Optional<Curriculum> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = "major")
    Optional<Curriculum> findByMajor_IdAndCohortIdAndTrainingTypeAndDeletedFalse(
            Long majorId, Long cohortId, TrainingType trainingType);

    @EntityGraph(attributePaths = "major")
    List<Curriculum> findAllByMajor_IdInAndCohortIdInAndDeletedFalse(
            Collection<Long> majorIds, Collection<Long> cohortIds);
}
