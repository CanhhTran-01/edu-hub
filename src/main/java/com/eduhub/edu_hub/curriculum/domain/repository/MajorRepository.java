package com.eduhub.edu_hub.curriculum.domain.repository;

import com.eduhub.edu_hub.curriculum.domain.model.Major;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MajorRepository extends JpaRepository<Major, Long> {
    @Override
    @EntityGraph(attributePaths = "faculty")
    List<Major> findAllById(Iterable<Long> ids);
}
