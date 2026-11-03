package com.eduhub.edu_hub.curriculum.application;

import com.eduhub.edu_hub.curriculum.api.dto.CurriculumOpeningSubjectResponse;
import com.eduhub.edu_hub.curriculum.api.dto.CurriculumResponse;
import com.eduhub.edu_hub.curriculum.api.dto.SubjectResponse;
import com.eduhub.edu_hub.curriculum.api.dto.SubjectSimpleResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CurriculumFacade {
    /** Read facet phục vụ mở lớp, không làm giả các capability chưa triển khai của Facade đầy đủ. */
    interface ClassOpening {
        List<CurriculumOpeningSubjectResponse> findPlanningSubjects();

        List<SubjectSimpleResponse> findSubjectsByIds(Set<Long> ids);

        List<SubjectSimpleResponse> requireOpenableSubjects(Set<Long> ids);
    }

    Optional<SubjectResponse> findSubjectById(Long subjectId);

    Optional<SubjectResponse> findSubjectByCode(String subjectCode);

    List<SubjectResponse> findPrerequisites(Long subjectId);

    /** Kiểm tra sinh viên đã hoàn thành đủ môn tiên quyết chưa */
    boolean checkPrerequisitesMet(Long studentId, Long subjectId);

    Optional<CurriculumResponse> findCurriculum(Long majorId, Long cohortId);

    /** Lấy danh sách học phần còn thiếu của sinh viên theo CTĐT */
    List<SubjectResponse> findRemainingSubjects(Long studentId);
}
