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

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {
    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    Optional<Student> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    List<Student> findAllByDeletedFalseAndActivityStatus(ActivityStatus activityStatus);

    @Override
    @EntityGraph(attributePaths = {"administrativeClass", "administrativeClass.cohort"})
    Page<Student> findAll(Specification<Student> specification, Pageable pageable);
}
