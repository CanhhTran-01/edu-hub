package com.eduhub.edu_hub.curriculum.domain.repository;

import com.eduhub.edu_hub.curriculum.domain.model.Major;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MajorRepository extends JpaRepository<Major, Long> {
    List<Major> findAllByDeletedFalseOrderByMajorCodeAscIdAsc();

    List<Major> findByDeletedFalseOrderById();

    Optional<Major> findByIdAndDeletedFalse(Long id);
}
