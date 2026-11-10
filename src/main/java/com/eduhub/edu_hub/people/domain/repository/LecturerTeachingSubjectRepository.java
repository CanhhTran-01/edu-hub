package com.eduhub.edu_hub.people.domain.repository;

import com.eduhub.edu_hub.people.domain.model.LecturerTeachingSubject;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LecturerTeachingSubjectRepository extends JpaRepository<LecturerTeachingSubject, Long> {
    List<LecturerTeachingSubject> findByLecturerIdInAndDeletedFalseOrderByLecturerIdAscSubjectIdAsc(
            Collection<Long> lecturerIds);
}
