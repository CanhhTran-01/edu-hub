package com.eduhub.edu_hub.curriculum.domain.repository;

import com.eduhub.edu_hub.curriculum.domain.entity.Subject;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface SubjectRepository extends JpaRepository<Subject, Long>, JpaSpecificationExecutor<Subject> {
    boolean existsBySubjectCode(String subjectCode);

    boolean existsBySubjectName(String subjectName);

    boolean existsBySubjectNameAndIdNot(String subjectName, Long id);

    Optional<Subject> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    List<Subject> findAllByIdInAndDeletedFalseOrderByIdAsc(Set<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Subject> findAllByDeletedFalseOrderByIdAsc();
}
