package com.eduhub.edu_hub.scheduling.domain.repository;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ModuleClassRepository extends JpaRepository<ModuleClass, Long>, JpaSpecificationExecutor<ModuleClass> {
    @Query("select distinct moduleClass.semester.id from ModuleClass moduleClass "
            + "where moduleClass.deleted = false and moduleClass.semester.deleted = false "
            + "and moduleClass.status in :statuses order by moduleClass.semester.id")
    List<Long> findSemesterIdsForLifecycle(@Param("statuses") java.util.Collection<ModuleClassStatus> statuses);

    @Override
    @EntityGraph(attributePaths = "semester")
    Page<ModuleClass> findAll(
            org.springframework.data.jpa.domain.Specification<ModuleClass> specification, Pageable pageable);

    @EntityGraph(attributePaths = "semester")
    Optional<ModuleClass> findByIdAndDeletedFalse(Long id);

    // Chỉ đọc ID để chưa đưa entity vào persistence context trước khi chờ khóa kỳ/lớp.
    @Query("select m.semester.id from ModuleClass m where m.id = :id and m.deleted = false")
    Optional<Long> findSemesterIdByIdAndDeletedFalse(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ModuleClass> findForUpdateByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = "semester")
    List<ModuleClass> findAllBySemester_IdOrderByIdAsc(Long semesterId);

    boolean existsBySemester_IdAndStatusNot(Long semesterId, ModuleClassStatus status);

    boolean existsBySemester_IdAndStatusInAndDeletedFalse(Long semesterId, Collection<ModuleClassStatus> statuses);
}
