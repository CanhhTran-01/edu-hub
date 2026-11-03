package com.eduhub.edu_hub.curriculum.domain.repository;

import com.eduhub.edu_hub.curriculum.domain.entity.Major;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MajorRepository extends JpaRepository<Major, Long> {
    Optional<Major> findByIdAndDeletedFalse(Long id);
    Optional<Major> findByMajorCodeIgnoreCaseAndDeletedFalse(String code);
}
