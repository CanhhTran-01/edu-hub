package com.eduhub.edu_hub.curriculum.application.facade.impl;

import com.eduhub.edu_hub.curriculum.api.dto.response.*;
import com.eduhub.edu_hub.curriculum.application.exception.SubjectException;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
import com.eduhub.edu_hub.curriculum.domain.repository.*;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class CurriculumRegistrationFacadeImpl implements CurriculumFacade.Registration {
    private final SubjectRepository subjectRepository;
    private final SubjectPrerequisiteRepository prerequisiteRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<SubjectResponse> requireRegistrationSubjects(Set<Long> subjectIds) {

        if (subjectIds.isEmpty()) return List.of();

        // Khóa đọc môn gốc đồng bộ với PUT tín chỉ và thay tiên quyết, giữ đến cuối transaction đăng ký.
        var subjects = subjectRepository.findAllByIdInAndDeletedFalseOrderByIdAsc(subjectIds);
        if (subjects.size() != subjectIds.size()) throw new SubjectException(ErrorCode.SUBJECT_NOT_FOUND);
        if (subjects.stream().anyMatch(subject -> subject.getStatus() != SubjectStatus.ACTIVE))
            throw new SubjectException(ErrorCode.SUBJECT_NOT_ACTIVE);

        var prerequisitesBySubjectId = prerequisiteRepository.findAllBySubject_IdInAndDeletedFalse(subjectIds).stream()
                .collect(Collectors.groupingBy(link -> link.getSubject().getId()));

        return subjects.stream()
                .map(subject -> SubjectResponse.builder()
                        .id(subject.getId())
                        .subjectCode(subject.getSubjectCode())
                        .subjectName(subject.getSubjectName())
                        .credits(subject.getCredits())
                        .totalTeachingPeriods(subject.getTotalTeachingPeriods())
                        .status(subject.getStatus())
                        .description(subject.getDescription())
                        // Tiên quyết đã ngừng mở vẫn là yêu cầu học tập; không bỏ điều kiện vì trạng thái danh mục.
                        .prerequisites(prerequisitesBySubjectId.getOrDefault(subject.getId(), List.of()).stream()
                                .map(link -> SubjectSimpleResponse.builder()
                                        .id(link.getPrerequisiteSubject().getId())
                                        .subjectCode(
                                                link.getPrerequisiteSubject().getSubjectCode())
                                        .subjectName(
                                                link.getPrerequisiteSubject().getSubjectName())
                                        .credits(link.getPrerequisiteSubject().getCredits())
                                        .build())
                                .toList())
                        .requiredBy(List.of())
                        .build())
                .toList();
    }
}
