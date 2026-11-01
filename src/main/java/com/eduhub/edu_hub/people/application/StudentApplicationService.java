package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.people.api.dto.StudentResponse;
import com.eduhub.edu_hub.people.api.dto.StudentSearchRequest;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentApplicationService {
    private final StudentRepository studentRepository;

    public PageResponse<StudentResponse> search(StudentSearchRequest request, int page, int size) {
        ActivityStatus status = null;
        if (request.getActivityStatus() != null && !request.getActivityStatus().isBlank()) {
            try {
                status = ActivityStatus.valueOf(request.getActivityStatus());
            } catch (IllegalArgumentException exception) {
                throw new BaseException(ErrorCode.VALIDATION_ERROR);
            }
        }
        ActivityStatus selectedStatus = status;
        Specification<Student> filters = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deleted")));

            String keyword = request.getKeyword();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("studentCode")), pattern),
                        builder.like(builder.lower(root.get("fullName")), pattern)));
            }

            if (request.getClassId() != null || request.getMajorId() != null || request.getCohortId() != null) {
                Join<Student, AdministrativeClass> administrativeClass =
                        root.join("administrativeClass", JoinType.LEFT);
                if (request.getClassId() != null) {
                    predicates.add(builder.equal(administrativeClass.get("id"), request.getClassId()));
                }
                if (request.getMajorId() != null) {
                    predicates.add(builder.equal(administrativeClass.get("majorId"), request.getMajorId()));
                }
                if (request.getCohortId() != null) {
                    Join<AdministrativeClass, Cohort> cohort = administrativeClass.join("cohort", JoinType.LEFT);
                    predicates.add(builder.equal(cohort.get("id"), request.getCohortId()));
                }
            }

            if (selectedStatus != null) {
                predicates.add(builder.equal(root.get("activityStatus"), selectedStatus));
            }
            if (request.getAcademicWarningStatus() != null
                    && !request.getAcademicWarningStatus().isBlank()) {
                predicates.add(builder.equal(root.get("academicWarningStatus"), request.getAcademicWarningStatus()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return PageMapper.toPageResponse(studentRepository
                .findAll(filters, PageRequest.of(page, size, Sort.by("id").ascending()))
                .map(this::toResponse));
    }

    public StudentResponse findById(Long id) {
        return studentRepository
                .findByIdAndDeletedFalse(id)
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
    }

    private StudentResponse toResponse(Student student) {
        AdministrativeClass administrativeClass = student.getAdministrativeClass();
        return StudentResponse.builder()
                .id(student.getId())
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .dateOfBirth(student.getDateOfBirth())
                .email(student.getEmail())
                .phone(student.getPhone())
                .className(administrativeClass == null ? null : administrativeClass.getClassName())
                .majorName(administrativeClass == null ? null : administrativeClass.getMajorName())
                .cohortCode(
                        administrativeClass == null
                                ? null
                                : administrativeClass.getCohort().getCohortCode())
                .trainingType(
                        administrativeClass == null
                                ? null
                                : administrativeClass.getTrainingType().name())
                .entranceScore(student.getEntranceScore())
                .targetGpa(student.getTargetGpa())
                .activityStatus(student.getActivityStatus())
                .academicWarningStatus(student.getAcademicWarningStatus())
                .createdAt(student.getCreatedAt())
                .build();
    }
}
