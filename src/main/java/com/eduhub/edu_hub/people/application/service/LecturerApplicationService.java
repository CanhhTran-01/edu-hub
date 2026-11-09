package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.people.api.dto.request.LecturerSearchRequest;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.exception.ProfileSearchException;
import com.eduhub.edu_hub.people.application.specification.LecturerSpecification;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Faculty;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LecturerApplicationService {
    private final LecturerRepository lecturerRepository;
    private final ProfileAccountEmailService profileAccountEmailService;

    public PageResponse<LecturerResponse> search(LecturerSearchRequest request) {

        LecturerStatus status = validateSearchRequest(request);

        var lecturers = lecturerRepository.findAll(
                LecturerSpecification.fromRequest(request, status),
                PageRequest.of(request.getPage(), request.getSize(), Sort.by("id")));

        var accountEmailsByUserId = profileAccountEmailService.findEmailsByUserIds(
                lecturers.getContent().stream().map(Lecturer::getUserId).toList());

        return PageMapper.toPageResponse(lecturers.map(lecturer -> toResponse(lecturer, accountEmailsByUserId)));
    }

    public LecturerResponse findById(Long id) {
        return lecturerRepository
                .findByIdAndDeletedFalse(id)
                .map(lecturer -> toResponse(
                        lecturer,
                        profileAccountEmailService.findEmailsByUserIds(
                                Collections.singletonList(lecturer.getUserId()))))
                .orElseThrow(() -> new BaseException(ErrorCode.LECTURER_NOT_FOUND));
    }

    public List<LecturerResponse> findByIds(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();

        // Không lọc INACTIVE: lớp cũ vẫn cần hiển thị giảng viên từng được phân công.
        var lecturers = lecturerRepository.findAllByIdInAndDeletedFalse(ids);

        var accountEmailsByUserId = profileAccountEmailService.findEmailsByUserIds(
                lecturers.stream().map(Lecturer::getUserId).toList());

        return lecturers.stream()
                .map(lecturer -> toResponse(lecturer, accountEmailsByUserId))
                .toList();
    }

    private LecturerStatus validateSearchRequest(LecturerSearchRequest request) {
        if (request.getPage() < 0 || request.getSize() < 1) {
            throw new ProfileSearchException();
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            try {
                return LecturerStatus.valueOf(request.getStatus().trim());
            } catch (IllegalArgumentException exception) {
                throw new ProfileSearchException();
            }
        }
        return null;
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
