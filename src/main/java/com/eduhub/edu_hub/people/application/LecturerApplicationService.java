package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.people.api.dto.LecturerResponse;
import com.eduhub.edu_hub.people.api.dto.LecturerSearchRequest;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Faculty;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LecturerApplicationService {
    private final LecturerRepository lecturerRepository;

    public PageResponse<LecturerResponse> search(LecturerSearchRequest request, int page, int size) {
        LecturerStatus status = null;
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            try {
                status = LecturerStatus.valueOf(request.getStatus().trim());
            } catch (IllegalArgumentException exception) {
                throw new BaseException(ErrorCode.VALIDATION_ERROR);
            }
        }
        LecturerStatus selectedStatus = status;
        Specification<Lecturer> filters = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deleted")));

            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                String pattern = pattern(request.getKeyword());
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("lecturerCode")), pattern),
                        builder.like(builder.lower(root.get("fullName")), pattern)));
            }
            if (request.getLecturerCode() != null && !request.getLecturerCode().isBlank()) {
                predicates.add(
                        builder.like(builder.lower(root.get("lecturerCode")), pattern(request.getLecturerCode())));
            }
            if (request.getFullName() != null && !request.getFullName().isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("fullName")), pattern(request.getFullName())));
            }
            if (request.getFacultyId() != null) {
                Join<Lecturer, Faculty> faculty = root.join("faculty", JoinType.INNER);
                predicates.add(builder.equal(faculty.get("id"), request.getFacultyId()));
                predicates.add(builder.isFalse(faculty.get("deleted")));
            }
            if (selectedStatus != null) {
                predicates.add(builder.equal(root.get("status"), selectedStatus));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return PageMapper.toPageResponse(lecturerRepository
                .findAll(filters, PageRequest.of(page, size, Sort.by("id").ascending()))
                .map(this::toResponse));
    }

    public LecturerResponse findById(Long id) {
        return lecturerRepository
                .findByIdAndDeletedFalse(id)
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.LECTURER_NOT_FOUND));
    }

    private String pattern(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private LecturerResponse toResponse(Lecturer lecturer) {
        Faculty faculty = lecturer.getFaculty();
        return LecturerResponse.builder()
                .id(lecturer.getId())
                .lecturerCode(lecturer.getLecturerCode())
                .fullName(lecturer.getFullName())
                .email(lecturer.getEmail())
                .phone(lecturer.getPhone())
                .facultyName(faculty == null || faculty.isDeleted() ? null : faculty.getFacultyName())
                .status(lecturer.getStatus())
                .build();
    }
}
