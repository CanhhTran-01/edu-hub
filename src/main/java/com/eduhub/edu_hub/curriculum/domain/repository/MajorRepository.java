package com.eduhub.edu_hub.curriculum.domain.repository;

import com.eduhub.edu_hub.curriculum.domain.model.Major;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MajorRepository extends JpaRepository<Major, Long> {

    Optional<Major> findByIdAndDeletedFalse(Long id);

    Optional<Major> findByMajorCodeIgnoreCaseAndDeletedFalse(String code);

    @Override
    @EntityGraph(attributePaths = "faculty")
    List<Major> findAllById(Iterable<Long> ids);
}
