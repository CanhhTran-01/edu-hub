package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.curriculum.api.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.api.dto.StudentResponse;
import com.eduhub.edu_hub.people.api.dto.StudentSearchRequest;
import com.eduhub.edu_hub.people.api.dto.UpdateStudentRequest;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.AdministrativeClassRepository;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StudentApplicationService {
    private final StudentRepository studentRepository;
    private final AdministrativeClassRepository administrativeClassRepository;
    private final CurriculumFacade.ReferenceData curriculum;

    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest request) {
        if (request.getStudentCode() != null) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }
        Student student = studentRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
        if (request.getFullName() != null) {
            if (request.getFullName().isBlank()) {
                throw new BaseException(ErrorCode.VALIDATION_ERROR);
            }
            student.setFullName(request.getFullName().trim());
        }
        if (request.getDateOfBirth() != null) student.setDateOfBirth(request.getDateOfBirth());
        if (request.getEmail() != null) student.setEmail(request.getEmail());
        if (request.getPhone() != null) student.setPhone(request.getPhone());
        if (request.getCitizenId() != null) student.setCitizenId(request.getCitizenId());
        if (request.getTargetGpa() != null) student.setTargetGpa(request.getTargetGpa());
        updateAcademicWarning(student, request);
        if (request.getAdministrativeClassId() != null) {
            AdministrativeClass administrativeClass = administrativeClassRepository
                    .findByIdAndDeletedFalse(request.getAdministrativeClassId())
                    .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));
            student.setAdministrativeClass(administrativeClass);
        }
        StudentResponse response = toResponse(studentRepository.save(student));
        log.info("Updated student profile: studentId={}, reason={}", id, request.getStatusReason());
        return response;
    }

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
        var students = studentRepository.findAll(
                filters, PageRequest.of(page, size, Sort.by("id").ascending()));
        var majorNames = majorNames(students.getContent());
        return PageMapper.toPageResponse(students.map(student -> toResponse(student, majorNames)));
    }

    public StudentResponse findById(Long id) {
        return studentRepository
                .findByIdAndDeletedFalse(id)
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
    }

    private StudentResponse toResponse(Student student) {
        return toResponse(student, majorNames(List.of(student)));
    }

    private Map<Long, String> majorNames(List<Student> students) {
        var ids = students.stream()
                .map(Student::getAdministrativeClass)
                .filter(Objects::nonNull)
                .map(AdministrativeClass::getMajorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return curriculum.findMajorsByIds(ids).stream()
                .collect(Collectors.toMap(MajorSimpleResponse::id, MajorSimpleResponse::majorName));
    }

    private void updateAcademicWarning(Student student, UpdateStudentRequest request) {
        if (request.getAcademicWarningStatus() != null) {
            String status = normalizeOptionalText(request.getAcademicWarningStatus());
            if (!Objects.equals(status, student.getAcademicWarningStatus())) {
                student.setAcademicWarningReason(null);
            }
            student.setAcademicWarningStatus(status);
            if (status == null) {
                student.setAcademicWarningReason(null);
                return;
            }
        }
        if (request.getAcademicWarningReason() != null) {
            String reason = normalizeOptionalText(request.getAcademicWarningReason());
            if (reason != null && normalizeOptionalText(student.getAcademicWarningStatus()) == null) {
                throw new BaseException(ErrorCode.VALIDATION_ERROR);
            }
            student.setAcademicWarningReason(reason);
        }
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private StudentResponse toResponse(Student student, Map<Long, String> majorNames) {
        AdministrativeClass administrativeClass = student.getAdministrativeClass();
        return StudentResponse.builder()
                .id(student.getId())
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .dateOfBirth(student.getDateOfBirth())
                .email(student.getEmail())
                .phone(student.getPhone())
                .citizenId(student.getCitizenId())
                .className(administrativeClass == null ? null : administrativeClass.getClassName())
                .majorName(administrativeClass == null ? null : majorNames.get(administrativeClass.getMajorId()))
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
                .academicWarningReason(student.getAcademicWarningReason())
                .createdAt(student.getCreatedAt())
                .build();
    }
}
