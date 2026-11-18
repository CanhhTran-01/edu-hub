package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassRosterSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassRosterResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassSimpleResponse;
import com.eduhub.edu_hub.scheduling.api.mapper.ModuleClassResponseMapper;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassRosterException;
import com.eduhub.edu_hub.scheduling.application.specification.ModuleClassSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.policy.ModuleClassRosterAccessPolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Đọc danh sách/chi tiết theo phạm vi người dùng; bản chụp DTO không chứa entity hoặc proxy. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ModuleClassRosterService {
    private final IdentityFacade identityFacade;
    private final PeopleFacade.Scheduling peopleFacade;
    private final CurriculumFacade.ClassOpening curriculumFacade;
    private final EnrollmentFacade.ClassRoster enrollmentFacade;
    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;
    private final ModuleClassApplicationService moduleClassApplicationService;

    private record RosterActor(Long userId, String role, Long lecturerId) {}

    public PageResponse<ModuleClassSimpleResponse> searchClasses(
            Jwt principal, Long semesterId, ModuleClassRosterSearchRequest request) {
        var actor = requireRosterActor(principal);
        requirePositiveId(semesterId);
        if (request == null || request.getPage() < 0 || request.getSize() < 1)
            throw new ModuleClassRosterException(ErrorCode.VALIDATION_ERROR);
        semesterRepository
                .findByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new ModuleClassRosterException(ErrorCode.SEMESTER_NOT_FOUND));

        var moduleClassPage = moduleClassRepository.findAll(
                ModuleClassSpecification.matchesRosterCriteria(semesterId, actor.lecturerId(), request.getKeyword()),
                PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "id")));
        if (moduleClassPage.isEmpty())
            return PageMapper.toPageResponse(
                    new PageImpl<>(List.of(), moduleClassPage.getPageable(), moduleClassPage.getTotalElements()));

        var subjectsById = curriculumFacade
                .findSubjectsByIds(moduleClassPage.stream()
                        .map(moduleClass -> moduleClass.getSubject().getId())
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SubjectSimpleResponse::getId, subject -> subject));
        var lecturerIds = moduleClassPage.stream()
                .map(moduleClass -> moduleClass.getLecturer() == null
                        ? null
                        : moduleClass.getLecturer().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var lecturerNamesById = lecturerIds.isEmpty()
                ? java.util.Map.<Long, String>of()
                : peopleFacade.findLecturersByIds(lecturerIds).stream()
                        .collect(Collectors.toMap(LecturerResponse::getId, LecturerResponse::getFullName));
        return PageMapper.toPageResponse(moduleClassPage.map(moduleClass ->
                ModuleClassResponseMapper.toSimpleResponse(moduleClass, subjectsById, lecturerNamesById)));
    }

    public ModuleClassRosterResponse findRoster(Jwt principal, Long moduleClassId) {
        var actor = requireRosterActor(principal);
        requirePositiveId(moduleClassId);
        var moduleClass = moduleClassRepository
                .findByIdAndDeletedFalse(moduleClassId)
                .orElseThrow(() -> new ModuleClassRosterException(ErrorCode.MODULE_CLASS_NOT_FOUND));
        Long assignedLecturerId = moduleClass.getLecturer() == null
                ? null
                : moduleClass.getLecturer().getId();
        try {
            ModuleClassRosterAccessPolicy.requireClassAccess(actor.role(), actor.lecturerId(), assignedLecturerId);
        } catch (ModuleClassRosterException exception) {
            log.warn("Từ chối xem danh sách lớp: actorUserId={}, moduleClassId={}", actor.userId(), moduleClassId);
            throw exception;
        }
        if (moduleClass.getSemester().isDeleted()) throw new ModuleClassRosterException(ErrorCode.SEMESTER_NOT_FOUND);
        if (moduleClass.getStatus() == ModuleClassStatus.CANCELLED)
            throw new ModuleClassRosterException(ErrorCode.MODULE_CLASS_CANCELLED);

        // Các facade đọc tham gia cùng transaction để thông tin lớp và danh sách sinh viên cùng bản chụp.
        return new ModuleClassRosterResponse(
                moduleClassApplicationService.findModuleClassById(moduleClassId),
                enrollmentFacade.findStudentsByModuleClassId(moduleClassId));
    }

    private RosterActor requireRosterActor(Jwt principal) {
        if (principal == null) throw new ModuleClassRosterException(ErrorCode.UNAUTHENTICATED);
        if (!AccessTokenPurpose.ACCESS.name().equals(principal.getClaim(AccessTokenPurpose.CLAIM_NAME)))
            throw new ModuleClassRosterException(ErrorCode.FIRST_LOGIN_REQUIRED);
        var currentUser = identityFacade.requireCurrentUser(principal);
        Long lecturerId = ModuleClassRosterAccessPolicy.requiresLecturerAssignment(currentUser.getRole())
                ? peopleFacade
                        .findLecturerIdByUserId(currentUser.getId())
                        .orElseThrow(() -> new ModuleClassRosterException(ErrorCode.UNAUTHORIZED))
                : null;
        return new RosterActor(currentUser.getId(), currentUser.getRole(), lecturerId);
    }

    private void requirePositiveId(Long identifier) {
        if (identifier == null || identifier <= 0) throw new ModuleClassRosterException(ErrorCode.VALIDATION_ERROR);
    }
}
