package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.LecturerSearchRequest;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final IdentityFacade identityFacade;

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

        var lecturers = lecturerRepository.findAll(
                filters, PageRequest.of(page, size, Sort.by("id").ascending()));
        var accountEmails = accountEmailsByUserId(lecturers.getContent());
        return PageMapper.toPageResponse(lecturers.map(lecturer -> toResponse(lecturer, accountEmails)));
    }

    public LecturerResponse findById(Long id) {
        return lecturerRepository
                .findByIdAndDeletedFalse(id)
                .map(lecturer -> toResponse(lecturer, accountEmailsByUserId(List.of(lecturer))))
                .orElseThrow(() -> new BaseException(ErrorCode.LECTURER_NOT_FOUND));
    }

    public List<LecturerResponse> findByIds(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        // Không lọc INACTIVE: lớp cũ vẫn cần hiển thị giảng viên từng được phân công.
        var lecturers = lecturerRepository.findAllByIdInAndDeletedFalse(ids);
        var accountEmails = accountEmailsByUserId(lecturers);
        return lecturers.stream()
                .map(lecturer -> toResponse(lecturer, accountEmails))
                .toList();
    }

    private String pattern(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private Map<Long, String> accountEmailsByUserId(List<Lecturer> lecturers) {
        var userIds = lecturers.stream()
                .map(Lecturer::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        return identityFacade.findAccountEmailsByUserIds(userIds).stream()
                .filter(account -> account.email() != null)
                .collect(Collectors.toMap(UserAccountEmailResponse::userId, UserAccountEmailResponse::email));
    }

    private LecturerResponse toResponse(Lecturer lecturer, Map<Long, String> accountEmailsByUserId) {
        Faculty faculty = lecturer.getFaculty();
        return LecturerResponse.builder()
                .id(lecturer.getId())
                .lecturerCode(lecturer.getLecturerCode())
                .fullName(lecturer.getFullName())
                .email(lecturer.getUserId() == null ? null : accountEmailsByUserId.get(lecturer.getUserId()))
                .phone(lecturer.getPhone())
                .dateOfBirth(lecturer.getDateOfBirth())
                .citizenId(lecturer.getCitizenId())
                .facultyId(faculty == null ? null : faculty.getId())
                .facultyName(faculty == null || faculty.isDeleted() ? null : faculty.getFacultyName())
                .status(lecturer.getStatus())
                .build();
    }
}
