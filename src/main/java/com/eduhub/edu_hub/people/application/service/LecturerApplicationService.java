package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.api.dto.request.LecturerSearchRequest;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.api.mapper.LecturerResponseMapper;
import com.eduhub.edu_hub.people.application.exception.ProfileSearchException;
import com.eduhub.edu_hub.people.application.specification.LecturerSpecification;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.model.LecturerTeachingSubject;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerTeachingSubjectRepository;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final LecturerTeachingSubjectRepository lecturerTeachingSubjectRepository;
    private final CurriculumFacade.TeachingSubjects curriculumTeachingSubjects;
    private final LecturerResponseMapper lecturerResponseMapper;

    public PageResponse<LecturerResponse> search(LecturerSearchRequest request) {

        LecturerStatus status = validateSearchRequest(request);

        var lecturers = lecturerRepository.findAll(
                LecturerSpecification.fromRequest(request, status),
                PageRequest.of(request.getPage(), request.getSize(), Sort.by("id")));

        var accountEmailsByUserId = profileAccountEmailService.findEmailsByUserIds(
                lecturers.getContent().stream().map(Lecturer::getUserId).toList());

        var teachingSubjectsByLecturerId = findTeachingSubjects(lecturers.getContent());
        return PageMapper.toPageResponse(
                lecturers.map(lecturer -> toResponse(lecturer, accountEmailsByUserId, teachingSubjectsByLecturerId)));
    }

    public LecturerResponse findById(Long id) {
        return lecturerRepository
                .findByIdAndDeletedFalse(id)
                .map(lecturer -> toResponse(
                        lecturer,
                        profileAccountEmailService.findEmailsByUserIds(Collections.singletonList(lecturer.getUserId())),
                        findTeachingSubjects(List.of(lecturer))))
                .orElseThrow(() -> new BaseException(ErrorCode.LECTURER_NOT_FOUND));
    }

    public List<LecturerResponse> findByIds(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();

        // Không lọc INACTIVE: lớp cũ vẫn cần hiển thị giảng viên từng được phân công.
        var lecturers = lecturerRepository.findAllByIdInAndDeletedFalse(ids);

        var accountEmailsByUserId = profileAccountEmailService.findEmailsByUserIds(
                lecturers.stream().map(Lecturer::getUserId).toList());

        var teachingSubjectsByLecturerId = findTeachingSubjects(lecturers);
        return lecturers.stream()
                .map(lecturer -> toResponse(lecturer, accountEmailsByUserId, teachingSubjectsByLecturerId))
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

    private Map<Long, List<SubjectSimpleResponse>> findTeachingSubjects(List<Lecturer> lecturers) {
        Set<Long> lecturerIds =
                lecturers.stream().map(Lecturer::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (lecturerIds.isEmpty()) return Map.of();
        var teachingLinks =
                lecturerTeachingSubjectRepository.findByLecturerIdInAndDeletedFalseOrderByLecturerIdAscSubjectIdAsc(
                        lecturerIds);
        if (teachingLinks.isEmpty()) return Map.of();
        Set<Long> subjectIds = teachingLinks.stream()
                .map(LecturerTeachingSubject::getSubjectId)
                .collect(Collectors.toSet());
        // Đọc môn theo lô, giữ tên môn lịch sử; mapper không được tự gọi Curriculum.
        var subjectsById = curriculumTeachingSubjects.findTeachingSubjectsByIds(subjectIds).stream()
                .collect(Collectors.toMap(SubjectSimpleResponse::getId, Function.identity()));
        Map<Long, List<SubjectSimpleResponse>> teachingSubjectsByLecturerId = new HashMap<>();
        for (var teachingLink : teachingLinks) {
            var subject = subjectsById.get(teachingLink.getSubjectId());
            if (subject != null)
                teachingSubjectsByLecturerId
                        .computeIfAbsent(teachingLink.getLecturerId(), lecturerId -> new ArrayList<>())
                        .add(subject);
        }
        return teachingSubjectsByLecturerId;
    }

    private LecturerResponse toResponse(
            Lecturer lecturer,
            Map<Long, String> accountEmailsByUserId,
            Map<Long, List<SubjectSimpleResponse>> teachingSubjectsByLecturerId) {
        return lecturerResponseMapper.toLecturerResponse(
                lecturer,
                lecturer.getUserId() == null ? null : accountEmailsByUserId.get(lecturer.getUserId()),
                teachingSubjectsByLecturerId.getOrDefault(lecturer.getId(), List.of()));
    }
}
