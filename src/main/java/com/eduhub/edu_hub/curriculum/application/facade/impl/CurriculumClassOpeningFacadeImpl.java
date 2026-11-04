package com.eduhub.edu_hub.curriculum.application.facade.impl;

import com.eduhub.edu_hub.curriculum.api.dto.*;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.*;
import com.eduhub.edu_hub.curriculum.domain.model.*;
import com.eduhub.edu_hub.curriculum.domain.repository.*;
import com.eduhub.edu_hub.shared.exception.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurriculumClassOpeningFacadeImpl implements CurriculumFacade.ClassOpening {
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final SubjectRepository subjectRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CurriculumOpeningSubjectResponse> findPlanningSubjects() {
        return curriculumSubjectRepository.findAllByDeletedFalse().stream()
                .filter(link -> link.getSubjectType() == SubjectType.REQUIRED // môn bắt buộc
                        && !link.getSubject().isDeleted() // môn chưa bị xóa
                        && link.getSubject().getStatus() == SubjectStatus.ACTIVE // môn phải active
                        && !link.getCurriculum().isDeleted() // CTĐT chưa bị xóa
                        && link.getCurriculum().getStatus() == CatalogStatus.ACTIVE // CTĐT phải active
                        && !link.getCurriculum().getMajor().isDeleted() // ngành chưa bị xóa
                        && link.getCurriculum().getMajor().getStatus() == CatalogStatus.ACTIVE) // ngành phải active
                .map(link -> CurriculumOpeningSubjectResponse.builder()
                        .curriculumCode(link.getCurriculum().getCurriculumCode())
                        .subjectId(link.getSubject().getId())
                        .subjectCode(link.getSubject().getSubjectCode())
                        .subjectName(link.getSubject().getSubjectName())
                        .credits(link.getSubject().getCredits())
                        .recommendSemester(link.getRecommendSemester())
                        .build())
                .sorted(Comparator.comparing(CurriculumOpeningSubjectResponse::getCurriculumCode)
                        .thenComparingInt(CurriculumOpeningSubjectResponse::getRecommendSemester)
                        .thenComparing(CurriculumOpeningSubjectResponse::getSubjectCode))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectSimpleResponse> findSubjectsByIds(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        // Bao gồm môn đã ẩn/ngừng sử dụng để vẫn xem được thông tin lớp lịch sử.
        return subjectRepository.findAllById(ids).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public List<SubjectSimpleResponse> requireOpenableSubjects(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        // Giữ khóa đọc đến khi transaction mở lớp kết thúc, tránh ngừng sử dụng môn giữa lúc lưu.
        var subjects = subjectRepository.findAllByIdInAndDeletedFalseOrderByIdAsc(ids);
        if (subjects.size() != ids.size()) throw new BaseException(ErrorCode.SUBJECT_NOT_FOUND);
        if (subjects.stream().anyMatch(subject -> subject.getStatus() != SubjectStatus.ACTIVE))
            throw new BaseException(ErrorCode.SUBJECT_NOT_OPENABLE);
        return subjects.stream().map(this::toResponse).toList();
    }

    private SubjectSimpleResponse toResponse(Subject subject) {
        return SubjectSimpleResponse.builder()
                .id(subject.getId())
                .subjectCode(subject.getSubjectCode())
                .subjectName(subject.getSubjectName())
                .credits(subject.getCredits())
                .build();
    }
}
