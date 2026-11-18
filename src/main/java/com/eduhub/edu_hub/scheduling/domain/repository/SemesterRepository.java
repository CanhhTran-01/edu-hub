package com.eduhub.edu_hub.scheduling.domain.repository;

import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemesterRepository extends JpaRepository<Semester, Long>, JpaSpecificationExecutor<Semester> {
    List<Semester> findAllByIdInAndDeletedFalseOrderByStartDateDescIdDesc(Collection<Long> ids);

    @Query("select semester from Semester semester where semester.deleted = false "
            + "and ((semester.startDate <= :today and semester.endDate >= :today) "
            + "or (semester.registrationStart <= :today and semester.registrationEnd >= :today)) "
            + "order by semester.startDate desc, semester.id desc")
    List<Semester> findCurrentSemesterCandidates(@Param("today") LocalDate today);

    boolean existsBySemesterName(String semesterName);

    boolean existsBySemesterNameAndIdNot(String semesterName, Long id);

    Optional<Semester> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Semester> findForUpdateByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Semester> findForRegistrationByIdAndDeletedFalse(Long id);
}
