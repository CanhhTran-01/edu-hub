package com.eduhub.edu_hub.curriculum.domain.repository;

import com.eduhub.edu_hub.curriculum.domain.model.SubjectPrerequisite;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectPrerequisiteRepository extends JpaRepository<SubjectPrerequisite, Long> {
    @EntityGraph(attributePaths = {"subject", "prerequisiteSubject"})
    List<SubjectPrerequisite> findAllByDeletedFalse();

    @EntityGraph(attributePaths = {"subject", "prerequisiteSubject"})
    List<SubjectPrerequisite> findAllBySubject_IdInAndDeletedFalse(Collection<Long> subjectIds);

    @EntityGraph(attributePaths = {"subject", "prerequisiteSubject"})
    List<SubjectPrerequisite> findAllByPrerequisiteSubject_IdInAndDeletedFalse(Collection<Long> subjectIds);
}
