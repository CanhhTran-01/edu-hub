package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.Lecturer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LecturerRepository extends JpaRepository<Lecturer, Long>, JpaSpecificationExecutor<Lecturer> {
    @EntityGraph(attributePaths = "faculty")
    Optional<Lecturer> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = "faculty")
    List<Lecturer> findAllByIdInAndDeletedFalse(Collection<Long> ids);
}
