package com.eduhub.edu_hub.curriculum.domain.repository;

import com.eduhub.edu_hub.curriculum.domain.entity.CurriculumSubject;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface CurriculumSubjectRepository extends JpaRepository<CurriculumSubject, Long> {
    @EntityGraph(attributePaths = {"curriculum", "curriculum.major", "subject"})
    List<CurriculumSubject> findAllByDeletedFalse();
}
