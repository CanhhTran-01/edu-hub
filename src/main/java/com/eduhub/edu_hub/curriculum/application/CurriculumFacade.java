package com.eduhub.edu_hub.curriculum.application;

import com.eduhub.edu_hub.curriculum.api.dto.CurriculumResponse;
import com.eduhub.edu_hub.curriculum.api.dto.SubjectResponse;
import java.util.List;
import java.util.Optional;

public interface CurriculumFacade {
    Optional<SubjectResponse> findSubjectById(Long subjectId);

    Optional<SubjectResponse> findSubjectByCode(String subjectCode);

    List<SubjectResponse> findPrerequisites(Long subjectId);

    /** Kiểm tra sinh viên đã hoàn thành đủ môn tiên quyết chưa */
    boolean checkPrerequisitesMet(Long studentId, Long subjectId);

    Optional<CurriculumResponse> findCurriculum(Long majorId, Long cohortId);

    /** Lấy danh sách học phần còn thiếu của sinh viên theo CTĐT */
    List<SubjectResponse> findRemainingSubjects(Long studentId);
}
