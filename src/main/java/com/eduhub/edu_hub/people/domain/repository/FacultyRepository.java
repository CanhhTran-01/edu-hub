package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.Faculty;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select faculty from Faculty faculty where faculty.id = :facultyId and faculty.deleted = false and faculty.active = true")
    Optional<Faculty> findActiveFacultyForCodeAllocation(@Param("facultyId") Long facultyId);
}
