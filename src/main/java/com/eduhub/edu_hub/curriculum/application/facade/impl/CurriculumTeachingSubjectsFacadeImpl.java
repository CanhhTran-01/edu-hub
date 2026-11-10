package com.eduhub.edu_hub.curriculum.application.facade.impl;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.exception.TeachingSubjectException;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.curriculum.domain.repository.SubjectRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurriculumTeachingSubjectsFacadeImpl implements CurriculumFacade.TeachingSubjects {
    private final SubjectRepository subjectRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<SubjectSimpleResponse> requireActiveTeachingSubjects(Set<Long> subjectIds) {
        if (subjectIds.isEmpty()) return List.of();
        // Khóa đọc theo ID tăng dần, giữ tới hết transaction tạo giảng viên để tránh môn bị ngừng giữa chừng.
        List<Subject> teachingSubjects = subjectRepository.findAllByIdInAndDeletedFalseOrderByIdAsc(subjectIds);
        if (teachingSubjects.size() != subjectIds.size()) {
            throw new TeachingSubjectException(ErrorCode.SUBJECT_NOT_FOUND);
        }
        if (teachingSubjects.stream().anyMatch(subject -> subject.getStatus() != SubjectStatus.ACTIVE)) {
            throw new TeachingSubjectException(ErrorCode.SUBJECT_NOT_TEACHABLE);
        }
        return teachingSubjects.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectSimpleResponse> findTeachingSubjectsByIds(Set<Long> subjectIds) {
        if (subjectIds.isEmpty()) return List.of();
        // Hiển thị cả môn lịch sử đã ngừng/ẩn; việc đó không cho phép chọn lại môn khi tạo hồ sơ mới.
        return subjectRepository.findAllById(subjectIds).stream()
                .sorted(Comparator.comparing(Subject::getId))
                .map(this::toResponse)
                .toList();
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
