package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.UpdateLecturerProfileRequest;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.api.mapper.LecturerResponseMapper;
import com.eduhub.edu_hub.people.application.exception.LecturerProfileException;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.model.LecturerTeachingSubject;
import com.eduhub.edu_hub.people.domain.policy.LecturerProfileUpdatePolicy;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerTeachingSubjectRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cập nhật thông tin cá nhân và năng lực giảng dạy; không cấp lại mã/tài khoản hoặc đổi khoa/trạng thái. */
@Service
@RequiredArgsConstructor
@Slf4j
public class LecturerAdminUpdateService {
    private final LecturerRepository lecturerRepository;
    private final LecturerTeachingSubjectRepository lecturerTeachingSubjectRepository;
    private final IdentityFacade identityFacade;
    private final CurriculumFacade.TeachingSubjects curriculumTeachingSubjects;
    private final ProfileAccountEmailService profileAccountEmailService;
    private final LecturerResponseMapper lecturerResponseMapper;

    @Transactional
    public LecturerResponse updateLecturerProfile(
            Jwt authenticatedJwt, Long lecturerId, UpdateLecturerProfileRequest request) {
        if (authenticatedJwt == null) throw new LecturerProfileException(ErrorCode.UNAUTHENTICATED);

        // TODO(AUTHORIZATION): Khi bật phân quyền, chỉ ACADEMIC_ADMIN được cập nhật; SYSTEM_ADMIN không có
        //  ngoại lệ. Hiện chỉ kiểm tra JWT/User/phiên, chưa kiểm tra role, giống PUT hồ sơ sinh viên.
        var currentUser = identityFacade.requireCurrentUser(authenticatedJwt);

        // Chuẩn hóa và kiểm tra toàn bộ đầu vào trước khi thay đổi entity hoặc gọi facade ghi email.
        String fullName = LecturerProfileUpdatePolicy.normalizeFullName(request.getFullName());
        String phone = LecturerProfileUpdatePolicy.normalizePhone(request.getPhone());
        String citizenId = LecturerProfileUpdatePolicy.normalizeCitizenId(request.getCitizenId());
        String updateReason = LecturerProfileUpdatePolicy.normalizeUpdateReason(request.getReason());

        Set<Long> teachingSubjectIds =
                LecturerProfileUpdatePolicy.normalizeTeachingSubjectIds(request.getTeachingSubjectIds());

        LecturerProfileUpdatePolicy.validateDateOfBirth(request.getDateOfBirth(), LocalDate.now(ZoneOffset.UTC));

        // Mọi cập nhật danh sách môn phải khóa cùng hồ sơ gốc; thứ tự khóa: Lecturer -> môn theo ID -> User.
        // Không join khóa Faculty ở đây vì cập nhật thông tin cá nhân không thay đổi khoa hoặc MSGV.
        Lecturer lecturer = lecturerRepository
                .findLecturerProfileByIdForUpdate(lecturerId)
                .orElseThrow(() -> new LecturerProfileException(ErrorCode.LECTURER_NOT_FOUND));
        if (request.getEmail() != null && lecturer.getUserId() == null) {
            throw new LecturerProfileException(ErrorCode.USER_NOT_FOUND);
        }

        validateUniqueProfileChanges(lecturerId, citizenId, request.getEmail());

        List<LecturerTeachingSubject> existingTeachingLinks =
                lecturerTeachingSubjectRepository.findByLecturerIdOrderBySubjectIdAsc(lecturerId);
        List<SubjectSimpleResponse> teachingSubjects = teachingSubjectIds == null
                ? findCurrentTeachingSubjects(existingTeachingLinks)
                : curriculumTeachingSubjects.requireActiveTeachingSubjects(teachingSubjectIds);

        // Email có giá trị mới được ghi vào User trong transaction hiện tại; lỗi phải rollback cả hồ sơ/môn.
        // Bỏ field/null giữ nguyên, chuỗi trắng đã được DTO giữ thành "" để yêu cầu xóa email.
        if (request.getEmail() != null) identityFacade.updateAccountEmail(lecturer.getUserId(), request.getEmail());

        // Sửa đúng instance đang quản lý, không dựng lại Lecturer để tránh mất mã/khoa/trạng thái/liên kết User.
        if (fullName != null) lecturer.setFullName(fullName);
        if (request.getDateOfBirth() != null) lecturer.setDateOfBirth(request.getDateOfBirth());
        if (phone != null) lecturer.setPhone(phone);
        if (citizenId != null) lecturer.setCitizenId(citizenId);

        lecturerRepository.save(lecturer);

        if (teachingSubjectIds != null)
            updateTeachingSubjectLinks(lecturerId, existingTeachingLinks, teachingSubjectIds);

        String accountEmail = lecturer.getUserId() == null
                ? null
                : profileAccountEmailService
                        .findEmailsByUserIds(List.of(lecturer.getUserId()))
                        .get(lecturer.getUserId());

        // TODO(AUDIT_LOG): Khi có audit trail, ghi actor, Lecturer ID, trường thay đổi, môn trước/sau, lý do
        log.info("Updated lecturer profile: actorId={}, lecturerId={}, reason={}",
                currentUser.getId(), lecturerId, updateReason);

        return lecturerResponseMapper.toLecturerResponse(lecturer, accountEmail, teachingSubjects);
    }

    private void validateUniqueProfileChanges(Long lecturerId, String citizenId, String email) {
        if (citizenId != null && lecturerRepository.existsByCitizenIdAndIdNotAndDeletedFalse(citizenId, lecturerId)) {
            throw new LecturerProfileException(
                    ErrorCode.DUPLICATE_LECTURER_CITIZEN_ID, "citizenId", "CCCD giảng viên đã tồn tại");
        }
        String normalizedEmail = TextsNormalizer.normalizeEmail(email);
        if (normalizedEmail == null) return;
        var userIdsWithEmail = identityFacade.findUserIdsByEmail(normalizedEmail);
        if (!userIdsWithEmail.isEmpty()
                && lecturerRepository.existsByUserIdInAndIdNotAndDeletedFalse(userIdsWithEmail, lecturerId)) {
            throw new LecturerProfileException(
                    ErrorCode.DUPLICATE_LECTURER_EMAIL, "email", "Email giảng viên đã tồn tại");
        }
    }

    private List<SubjectSimpleResponse> findCurrentTeachingSubjects(List<LecturerTeachingSubject> teachingLinks) {
        Set<Long> currentSubjectIds = teachingLinks.stream()
                .filter(teachingLink -> !teachingLink.isDeleted())
                .map(LecturerTeachingSubject::getSubjectId)
                .collect(Collectors.toSet());
        // Không gửi danh sách môn thì giữ cả môn lịch sử ngừng/ẩn, không kiểm tra lại điều kiện chọn môn mới.
        return currentSubjectIds.isEmpty()
                ? List.of()
                : curriculumTeachingSubjects.findTeachingSubjectsByIds(currentSubjectIds);
    }

    private void updateTeachingSubjectLinks(
            Long lecturerId, List<LecturerTeachingSubject> existingTeachingLinks, Set<Long> teachingSubjectIds) {

        List<LecturerTeachingSubject> changedTeachingLinks = new ArrayList<>();

        Set<Long> previouslyLinkedSubjectIds = existingTeachingLinks.stream()
                .map(LecturerTeachingSubject::getSubjectId)
                .collect(Collectors.toSet());

        for (LecturerTeachingSubject teachingLink : existingTeachingLinks) {
            boolean shouldBeDeleted = !teachingSubjectIds.contains(teachingLink.getSubjectId());
            if (teachingLink.isDeleted() != shouldBeDeleted) {
                // Khôi phục cùng bản ghi khi chọn lại môn; unique giảng viên–môn bao gồm cả liên kết đã xóa.
                teachingLink.setDeleted(shouldBeDeleted);
                changedTeachingLinks.add(teachingLink);
            }
        }

        for (Long subjectId : teachingSubjectIds) {
            if (!previouslyLinkedSubjectIds.contains(subjectId)) {
                changedTeachingLinks.add(LecturerTeachingSubject.builder()
                        .lecturerId(lecturerId)
                        .subjectId(subjectId)
                        .build());
            }
        }
        if (!changedTeachingLinks.isEmpty()) lecturerTeachingSubjectRepository.saveAll(changedTeachingLinks);
    }
}
