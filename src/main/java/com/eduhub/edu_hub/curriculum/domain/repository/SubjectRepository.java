package com.eduhub.edu_hub.curriculum.domain.repository;

import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface SubjectRepository extends JpaRepository<Subject, Long>, JpaSpecificationExecutor<Subject> {
    @org.springframework.data.jpa.repository.Query(
            "select subject.id from Subject subject where lower(subject.subjectCode) like :keywordPattern escape '!' "
                    + "or lower(subject.subjectName) like :keywordPattern escape '!'")
    Set<Long> findSubjectIdsByKeyword(
            @org.springframework.data.repository.query.Param("keywordPattern") String keywordPattern);

    // Sequence PostgreSQL cấp số nguyên tử; rollback hoặc xóa mềm không thu hồi STT đã lấy.
    @org.springframework.data.jpa.repository.Query(value = "select nextval('subject_code_seq')", nativeQuery = true)
    Long allocateNextSubjectSequence();

    boolean existsBySubjectCode(String subjectCode);

    boolean existsBySubjectName(String subjectName);

    boolean existsBySubjectNameAndIdNot(String subjectName, Long id);

    Optional<Subject> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select subject from Subject subject where subject.id = :id and subject.deleted = false")
    Optional<Subject> findForUpdateById(@org.springframework.data.repository.query.Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    List<Subject> findAllByIdInAndDeletedFalseOrderByIdAsc(Set<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Subject> findAllByDeletedFalseOrderByIdAsc();
}
