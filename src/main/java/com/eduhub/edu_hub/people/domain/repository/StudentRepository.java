package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByIdAndDeletedFalse(Long id);

    @Query("""
            select s from Student s
            where s.deleted = false
              and (:keyword is null or lower(s.studentCode) like lower(concat('%', :keyword, '%'))
                   or lower(s.fullName) like lower(concat('%', :keyword, '%')))
              and (:classId is null or s.classId = :classId)
              and (:activityStatus is null or s.activityStatus = :activityStatus)
              and (:academicWarningStatus is null or s.academicWarningStatus = :academicWarningStatus)
            """)
    Page<Student> search(
            @Param("keyword") String keyword,
            @Param("classId") Long classId,
            @Param("activityStatus") ActivityStatus activityStatus,
            @Param("academicWarningStatus") String academicWarningStatus,
            Pageable pageable);
}
