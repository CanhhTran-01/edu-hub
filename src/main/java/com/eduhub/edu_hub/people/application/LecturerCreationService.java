package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.identity.application.command.CreateLecturerAccountCommand;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.CreateLecturerRequest;
import com.eduhub.edu_hub.people.api.dto.response.CreateLecturerResponse;
import com.eduhub.edu_hub.people.api.dto.response.LecturerNotificationResponse;
import com.eduhub.edu_hub.people.application.code.LecturerCodeGenerator;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.repository.FacultyRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class LecturerCreationService {
    private static final String INITIAL_PASSWORD = "12345";
    private static final int MAX_REASON_LENGTH = 500;

    private final LecturerRepository lecturerRepository;
    private final FacultyRepository facultyRepository;
    private final LecturerCodeGenerator lecturerCodeGenerator;
    private final IdentityFacade identityFacade;
    private final TransactionTemplate transactionTemplate;
    private final LecturerApplicationService lecturerApplicationService;
    private final LecturerAccountNotificationService accountNotificationService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateLecturerResponse create(CreateLecturerRequest request, String token) {
        identityFacade.requireAcademicAdmin(token);

        CreatedLecturer created = transactionTemplate.execute(status -> createInTransaction(request));
        if (created == null) {
            throw new BaseException(ErrorCode.UNEXPECTED_ERROR);
        }

        // Gửi thông báo sau khi transaction tạo tài khoản và hồ sơ đã commit.
        LecturerNotificationResponse notification = accountNotificationService.send(created.id(), INITIAL_PASSWORD);
        return new CreateLecturerResponse(
                lecturerApplicationService.findById(created.id()),
                created.warnings(),
                notification.notificationStatus(),
                notification.notificationError());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LecturerNotificationResponse resend(Long id, String token) {
        identityFacade.requireAcademicAdmin(token);
        return accountNotificationService.send(id, INITIAL_PASSWORD);
    }

    private CreatedLecturer createInTransaction(CreateLecturerRequest request) {
        validateReason(request.getReason());

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String citizenId = normalizeCitizenId(request.getCitizenId());
        validateUniqueProfile(email, citizenId);

        var faculty = facultyRepository
                .findByIdAndDeletedFalseAndActiveTrue(request.getFacultyId())
                .orElseThrow(() -> new BaseException(ErrorCode.FACULTY_NOT_ACTIVE));

        String fullName = request.getFullName().trim();
        boolean possibleDuplicate = lecturerRepository.existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(
                fullName, request.getDateOfBirth());

        String lecturerCode = lecturerCodeGenerator.generate();
        Long userId = identityFacade.createLecturerAccountWithoutNotification(
                new CreateLecturerAccountCommand(lecturerCode, email, INITIAL_PASSWORD));

        Lecturer lecturer = new Lecturer();
        lecturer.setLecturerCode(lecturerCode);
        lecturer.setUserId(userId);
        lecturer.setFaculty(faculty);
        lecturer.setFullName(fullName);
        lecturer.setEmail(email);
        lecturer.setCitizenId(citizenId);
        lecturer.setDateOfBirth(request.getDateOfBirth());
        lecturer.setPhone(request.getPhone() == null ? null : request.getPhone().trim());
        lecturer.setStatus(LecturerStatus.ACTIVE);
        lecturer.setAccountNotificationStatus(AccountNotificationStatus.PENDING);

        lecturerRepository.saveAndFlush(lecturer);

        // TODO(AUDIT_LOG): Bổ sung lại nhật ký tạo giảng viên sau.

        List<String> warnings =
                possibleDuplicate ? List.of("Trùng họ tên và ngày sinh; cần kiểm tra thủ công") : List.of();

        return new CreatedLecturer(lecturer.getId(), warnings);
    }

    private void validateReason(String reason) {
        String normalizedReason = reason.trim();
        if (normalizedReason.isEmpty() || normalizedReason.length() > MAX_REASON_LENGTH) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateUniqueProfile(String email, String citizenId) {
        if (lecturerRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
            throw new BaseException(ErrorCode.DUPLICATE_LECTURER_EMAIL);
        }
        if (citizenId != null && lecturerRepository.existsByCitizenIdAndDeletedFalse(citizenId)) {
            throw new BaseException(ErrorCode.DUPLICATE_LECTURER_CITIZEN_ID);
        }
    }

    private String normalizeCitizenId(String citizenId) {

        if (citizenId == null) return null;

        String normalizedCitizenId = citizenId.replaceAll("\\s+", "");
        return normalizedCitizenId.isBlank() ? null : normalizedCitizenId;
    }

    private record CreatedLecturer(Long id, List<String> warnings) {}
}
