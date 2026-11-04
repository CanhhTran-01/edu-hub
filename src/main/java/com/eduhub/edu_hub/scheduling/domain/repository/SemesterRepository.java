package com.eduhub.edu_hub.scheduling.domain.repository;

import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface SemesterRepository extends JpaRepository<Semester, Long>, JpaSpecificationExecutor<Semester> {
    boolean existsBySemesterName(String semesterName);

    boolean existsBySemesterNameAndIdNot(String semesterName, Long id);

    Optional<Semester> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Semester> findForUpdateByIdAndDeletedFalse(Long id);
}
