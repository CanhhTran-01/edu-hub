package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.people.api.dto.StudentResponse;
import com.eduhub.edu_hub.people.api.dto.StudentSearchRequest;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentApplicationService {
    private final StudentRepository studentRepository;

    public PageResponse<StudentResponse> search(StudentSearchRequest request, int page, int size) {
        if (request.getMajorId() != null || request.getCohortId() != null) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }
        ActivityStatus status = null;
        if (request.getActivityStatus() != null && !request.getActivityStatus().isBlank()) {
            try {
                status = ActivityStatus.valueOf(request.getActivityStatus());
            } catch (IllegalArgumentException exception) {
                throw new BaseException(ErrorCode.VALIDATION_ERROR);
            }
        }
        String keyword = request.getKeyword();
        if (keyword != null && keyword.isBlank()) {
            keyword = null;
        }
        return PageMapper.toPageResponse(studentRepository
                .search(
                        keyword,
                        request.getClassId(),
                        status,
                        request.getAcademicWarningStatus(),
                        PageRequest.of(page, size, Sort.by("id").ascending()))
                .map(this::toResponse));
    }

    public StudentResponse findById(Long id) {
        return studentRepository
                .findByIdAndDeletedFalse(id)
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
    }

    private StudentResponse toResponse(Student student) {
        return StudentResponse.builder()
                .id(student.getId())
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .entranceScore(student.getEntranceScore())
                .targetGpa(student.getTargetGpa())
                .activityStatus(student.getActivityStatus())
                .academicWarningStatus(student.getAcademicWarningStatus())
                .createdAt(student.getCreatedAt())
                .build();
    }
}
