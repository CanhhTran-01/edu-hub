package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.people.api.dto.request.StudentSearchRequest;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.api.mapper.StudentResponseMapper;
import com.eduhub.edu_hub.people.application.exception.ProfileSearchException;
import com.eduhub.edu_hub.people.application.specification.StudentSpecification;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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
    private final CurriculumFacade.ReferenceData curriculum;
    private final StudentResponseMapper studentResponseMapper;
    private final ProfileAccountEmailService profileAccountEmailService;

    public PageResponse<StudentResponse> search(StudentSearchRequest request) {

        ActivityStatus activityStatus = validateSearchRequest(request);

        var students = studentRepository.findAll(
                StudentSpecification.fromRequest(request, activityStatus),
                PageRequest.of(request.getPage(), request.getSize(), Sort.by("id")));

        var majorNamesById = majorNames(students.getContent());
        var accountEmailsByUserId = profileAccountEmailService.findEmailsByUserIds(
                students.getContent().stream().map(Student::getUserId).toList());

        return PageMapper.toPageResponse(
                students.map(student -> toResponse(student, majorNamesById, accountEmailsByUserId)));
    }

    public StudentResponse findById(Long id) {
        return studentRepository
                .findByIdAndDeletedFalse(id)
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
    }

    private ActivityStatus validateSearchRequest(StudentSearchRequest request) {
        if (request.getPage() < 0 || request.getSize() < 1) {
            throw new ProfileSearchException();
        }
        if (request.getActivityStatus() != null && !request.getActivityStatus().isBlank()) {
            try {
                return ActivityStatus.valueOf(request.getActivityStatus());
            } catch (IllegalArgumentException exception) {
                throw new ProfileSearchException();
            }
        }
        return null;
    }

    private Map<Long, String> majorNames(List<Student> students) {
        var ids = students.stream()
                .map(Student::getAdministrativeClass)
                .filter(Objects::nonNull)
                .map(AdministrativeClass::getMajor)
                .filter(Objects::nonNull)
                .map(Major::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return curriculum.findMajorsByIds(ids).stream()
                .collect(Collectors.toMap(MajorSimpleResponse::id, MajorSimpleResponse::majorName));
    }

    private StudentResponse toResponse(Student student) {
        return toResponse(
                student,
                majorNames(List.of(student)),
                profileAccountEmailService.findEmailsByUserIds(Collections.singletonList(student.getUserId())));
    }

    private StudentResponse toResponse(
            Student student, Map<Long, String> majorNames, Map<Long, String> accountEmailsByUserId) {
        String accountEmail = student.getUserId() == null ? null : accountEmailsByUserId.get(student.getUserId());
        return studentResponseMapper.toStudentResponse(student, majorNames, accountEmail);
    }
}
