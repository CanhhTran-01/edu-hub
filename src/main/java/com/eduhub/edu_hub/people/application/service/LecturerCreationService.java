package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.api.dto.request.CreateLecturerAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.CreateLecturerRequest;
import com.eduhub.edu_hub.people.api.dto.response.CreateLecturerResponse;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.api.mapper.LecturerResponseMapper;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.model.LecturerTeachingSubject;
import com.eduhub.edu_hub.people.domain.policy.ProfileCreationPolicy;
import com.eduhub.edu_hub.people.domain.repository.FacultyRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerTeachingSubjectRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class LecturerCreationService {
    private final LecturerRepository lecturerRepository;
    private final FacultyRepository facultyRepository;
    private final LecturerCodeGenerator lecturerCodeGenerator;
    private final IdentityFacade identityFacade;
    private final TransactionTemplate transactionTemplate;
    private final LecturerTeachingSubjectRepository lecturerTeachingSubjectRepository;
    private final CurriculumFacade.TeachingSubjects curriculumTeachingSubjects;
    private final LecturerResponseMapper lecturerResponseMapper;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateLecturerResponse createLecturer(Jwt authenticatedJwt, CreateLecturerRequest request) {

        if (authenticatedJwt == null) throw new ProfileCreationException(ErrorCode.UNAUTHENTICATED);

        // TODO(AUTHORIZATION): Khi triển khai phân quyền, giới hạn tạo hồ sơ giảng viên cho ACADEMIC_ADMIN.
        var currentUser = identityFacade.requireCurrentUser(authenticatedJwt);

        CreatedLecturer createdLecturer =
                transactionTemplate.execute(transactionStatus -> createInTransaction(request));

        if (createdLecturer == null) throw new ProfileCreationException(ErrorCode.UNEXPECTED_ERROR);

        // TODO(AUDIT_LOG): Lưu actor, Lecturer ID, MSGV, môn có thể dạy,reason và kết quả khi có audit trail.

        log.info(
                "Created lecturer: actorId={}, lecturerId={}, lecturerCode={}, reason={}",
                currentUser.getId(),
                createdLecturer.lecturer().getId(),
                createdLecturer.lecturer().getLecturerCode(),
                createdLecturer.reason());

        // TODO(NOTIFICATION_DELIVERY): Hiện tại chỉ gửi đồng bộ một lần sau commit,
        //  không lưu trạng thái/lịch sử, không có resend/retry.
        //  Gửi lỗi hoặc tiến trình dừng giữa commit và gửi thì tài khoản vẫn tồn tại nhưng có thể chưa nhận email.
        AccountNotificationResponse notification = sendAccountEmail(createdLecturer.userId());

        if (!notification.emailSent()) {
            log.warn(
                    "Lecturer account email was not sent: userId={}, errorCode={}",
                    createdLecturer.userId(),
                    notification.emailError());
        }

        AccountNotificationStatus notificationStatus =
                notification.emailSent() ? AccountNotificationStatus.SENT : AccountNotificationStatus.FAILED;
        return new CreateLecturerResponse(
                createdLecturer.lecturer(),
                createdLecturer.warnings(),
                notificationStatus.name(),
                notification.emailError());
    }

    private CreatedLecturer createInTransaction(CreateLecturerRequest request) {

        String reason = ProfileCreationPolicy.normalizeReason(request.getReason());
        String email = TextsNormalizer.normalizeEmail(request.getEmail());
        String citizenId = ProfileCreationPolicy.normalizeOptionalCitizenId(request.getCitizenId());
        validateUniqueProfile(email, citizenId);

        Set<Long> teachingSubjectIds = ProfileCreationPolicy.requireTeachingSubjectIds(request.getTeachingSubjectIds());

        // Thứ tự lock: khoa -> môn theo ID -> bộ đếm, User/hồ sơ/liên kết cùng transaction tạo mới.
        var faculty = facultyRepository
                .findActiveFacultyForCodeAllocation(request.getFacultyId())
                .orElseThrow(() -> new ProfileCreationException(
                        ErrorCode.FACULTY_NOT_ACTIVE, "facultyId", "Khoa không tồn tại hoặc đã ngừng hoạt động"));

        List<SubjectSimpleResponse> teachingSubjects =
                curriculumTeachingSubjects.requireActiveTeachingSubjects(teachingSubjectIds);

        String fullName = request.getFullName().trim();
        boolean hasPossibleDuplicate = lecturerRepository.existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(
                fullName, request.getDateOfBirth());
        int lecturerSequence =
                lecturerCodeGenerator.allocateNextLecturerSequence(faculty, request.getRecruitmentYear());
        String lecturerCode =
                lecturerCodeGenerator.generateLecturerCode(faculty, request.getRecruitmentYear(), lecturerSequence);
        Long userId = identityFacade.createLecturerAccountWithoutNotification(
                new CreateLecturerAccountRequest(lecturerCode, email));

        Lecturer lecturer = Lecturer.builder()
                .lecturerCode(lecturerCode)
                .userId(userId)
                .faculty(faculty)
                .fullName(fullName)
                .citizenId(citizenId)
                .dateOfBirth(request.getDateOfBirth())
                .phone(request.getPhone() == null ? null : request.getPhone().trim())
                .recruitmentYear(request.getRecruitmentYear())
                .status(LecturerStatus.ACTIVE)
                .build();
        Lecturer savedLecturer = lecturerRepository.saveAndFlush(lecturer);
        lecturerTeachingSubjectRepository.saveAll(teachingSubjectIds.stream()
                .sorted()
                .map(subjectId -> LecturerTeachingSubject.builder()
                        .lecturerId(savedLecturer.getId())
                        .subjectId(subjectId)
                        .build())
                .toList());

        List<String> warnings =
                hasPossibleDuplicate ? List.of("Trùng họ tên và ngày sinh; cần kiểm tra thủ công") : List.of();

        // Dựng response từ dữ liệu đã tải; sau commit không tải lại hồ sơ hoặc ghi kết quả gửi.
        return new CreatedLecturer(
                lecturerResponseMapper.toLecturerResponse(savedLecturer, email, teachingSubjects),
                userId,
                warnings,
                reason);
    }

    private void validateUniqueProfile(String email, String citizenId) {
        if (email == null)
            throw new ProfileCreationException(ErrorCode.VALIDATION_ERROR, "email", "Email là thông tin bắt buộc");

        var userIdsWithEmail = identityFacade.findUserIdsByEmail(email);
        if (!userIdsWithEmail.isEmpty() && lecturerRepository.existsByUserIdInAndDeletedFalse(userIdsWithEmail)) {
            throw new ProfileCreationException(
                    ErrorCode.DUPLICATE_LECTURER_EMAIL, "email", "Email giảng viên đã tồn tại");
        }

        if (citizenId != null && lecturerRepository.existsByCitizenIdAndDeletedFalse(citizenId)) {
            throw new ProfileCreationException(
                    ErrorCode.DUPLICATE_LECTURER_CITIZEN_ID, "citizenId", "CCCD giảng viên đã tồn tại");
        }
    }

    private AccountNotificationResponse sendAccountEmail(Long userId) {
        try {
            return identityFacade.sendLecturerAccountNotification(userId);
        } catch (RuntimeException exception) {
            log.warn(
                    "Lecturer account email failed: userId={}, failureType={}",
                    userId,
                    exception.getClass().getSimpleName());
            return new AccountNotificationResponse(userId, false, "EMAIL_SEND_FAILED");
        }
    }

    private record CreatedLecturer(LecturerResponse lecturer, Long userId, List<String> warnings, String reason) {}
}
