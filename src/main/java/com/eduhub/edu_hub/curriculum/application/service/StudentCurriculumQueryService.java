package com.eduhub.edu_hub.curriculum.application.service;

import com.eduhub.edu_hub.curriculum.api.dto.response.*;
import com.eduhub.edu_hub.curriculum.api.dto.response.StudentCurriculumResponse.*;
import com.eduhub.edu_hub.curriculum.application.exception.StudentCurriculumException;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.CurriculumSubjectResponse;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.repository.SubjectPrerequisiteRepository;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import java.util.stream.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StudentCurriculumQueryService {
    private final IdentityFacade identityFacade;
    private final PeopleFacade.Enrollment peopleFacade;
    private final CurriculumFacade.Catalog curriculumCatalog;
    private final SubjectPrerequisiteRepository prerequisiteRepository;

    public StudentCurriculumResponse findMyCurriculum(Jwt principal) {
        if (principal == null) throw new StudentCurriculumException(ErrorCode.UNAUTHENTICATED);
        if (!AccessTokenPurpose.ACCESS.name().equals(principal.getClaim(AccessTokenPurpose.CLAIM_NAME)))
            throw new StudentCurriculumException(ErrorCode.FIRST_LOGIN_REQUIRED);
        var currentUser = identityFacade.requireCurrentUser(principal);
        // TODO(AUTHORIZATION): Kiểm tra quyền gọi API khi triển khai phân quyền; danh tính luôn lấy từ principal.
        var student = peopleFacade.findStudentByUserId(currentUser.getId());
        return findCurriculum(student.majorId(), student.cohortId(), student.trainingType())
                .orElseThrow(() -> new StudentCurriculumException(ErrorCode.CURRICULUM_NOT_FOUND));
    }

    public Optional<StudentCurriculumResponse> findCurriculum(Long majorId, Long cohortId, TrainingType trainingType) {
        if (majorId == null || cohortId == null || trainingType == null) return Optional.empty();
        return curriculumCatalog.findCurriculum(majorId, cohortId, trainingType).map(curriculum -> {
            int semesterCount = curriculum.getDurationSemesters();
            if (semesterCount < 1
                    || curriculum.getSubjects().stream()
                            .anyMatch(subject -> subject.getRecommendSemester() < 1
                                    || subject.getRecommendSemester() > semesterCount))
                throw new StudentCurriculumException(ErrorCode.CURRICULUM_DATA_INCOMPLETE);
            var subjectsBySemester = curriculum.getSubjects().stream()
                    .collect(Collectors.groupingBy(CurriculumSubjectResponse::getRecommendSemester));
            var semesters = IntStream.rangeClosed(1, semesterCount)
                    .mapToObj(semesterNumber -> new PlannedSemesterResponse(
                            semesterNumber,
                            subjectsBySemester.getOrDefault(semesterNumber, List.of()).stream()
                                    .sorted(Comparator.comparing(CurriculumSubjectResponse::getSubjectCode))
                                    .map(subject -> new PlannedSubjectResponse(
                                            subject.getSubjectId(),
                                            subject.getSubjectCode(),
                                            subject.getSubjectName(),
                                            subject.getCredits(),
                                            subject.getSubjectType()))
                                    .toList()))
                    .toList();
            return new StudentCurriculumResponse(
                    curriculum.getId(),
                    curriculum.getCurriculumCode(),
                    curriculum.getCurriculumName(),
                    curriculum.getMajorId(),
                    curriculum.getMajorName(),
                    curriculum.getCohortId(),
                    curriculum.getCohortCode(),
                    curriculum.getTrainingType(),
                    semesterCount,
                    curriculum.getRequiredGraduationCredits(),
                    curriculum.getTotalCredits(),
                    semesters);
        });
    }

    public List<SubjectPrerequisitesResponse> findPrerequisitesBySubjectIds(Set<Long> subjectIds) {
        if (subjectIds.isEmpty()) return List.of();
        var prerequisitesBySubjectId = prerequisiteRepository.findAllBySubject_IdInAndDeletedFalse(subjectIds).stream()
                .collect(Collectors.groupingBy(link -> link.getSubject().getId()));
        // Chỉ đọc cạnh hiện hành, giữ cả môn tiên quyết lịch sử đã ngừng/ẩn như điều kiện đăng ký hiện có.
        return subjectIds.stream()
                .sorted()
                .map(subjectId -> new SubjectPrerequisitesResponse(
                        subjectId,
                        prerequisitesBySubjectId.getOrDefault(subjectId, List.of()).stream()
                                .map(link -> {
                                    var prerequisite = link.getPrerequisiteSubject();
                                    return SubjectSimpleResponse.builder()
                                            .id(prerequisite.getId())
                                            .subjectCode(prerequisite.getSubjectCode())
                                            .subjectName(prerequisite.getSubjectName())
                                            .credits(prerequisite.getCredits())
                                            .totalTeachingPeriods(prerequisite.getTotalTeachingPeriods())
                                            .build();
                                })
                                .sorted(Comparator.comparing(SubjectSimpleResponse::getSubjectCode))
                                .toList()))
                .toList();
    }
}
