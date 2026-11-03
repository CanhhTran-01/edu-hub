package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.identity.application.CreateLecturerAccountCommand;
import com.eduhub.edu_hub.identity.application.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.*;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.repository.FacultyRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.shared.audit.AuditLog;
import com.eduhub.edu_hub.shared.audit.AuditLogRepository;
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
    private final LecturerRepository lecturers;
    private final FacultyRepository faculties;
    private final LecturerCodeGenerator codes;
    private final IdentityFacade identity;
    private final AuditLogRepository audits;
    private final TransactionTemplate transactions;
    private final LecturerApplicationService queries;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateLecturerResponse create(CreateLecturerRequest request, String token) {
        String actor = identity.requireAcademicAdmin(token);
        Created created = transactions.execute(tx -> {
            String reason = request.getReason().trim();
            if (reason.isEmpty() || reason.length() > 500) throw new BaseException(ErrorCode.VALIDATION_ERROR);
            String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
            String citizenId = request.getCitizenId() == null ? null : request.getCitizenId().replaceAll("\\s+", "");
            if (citizenId != null && citizenId.isBlank()) citizenId = null;
            if (lecturers.existsByEmailIgnoreCaseAndDeletedFalse(email))
                throw new BaseException(ErrorCode.DUPLICATE_LECTURER_EMAIL);
            if (citizenId != null && lecturers.existsByCitizenIdAndDeletedFalse(citizenId))
                throw new BaseException(ErrorCode.DUPLICATE_LECTURER_CITIZEN_ID);
            var faculty = faculties.findByIdAndDeletedFalseAndActiveTrue(request.getFacultyId())
                    .orElseThrow(() -> new BaseException(ErrorCode.FACULTY_NOT_ACTIVE));
            String name = request.getFullName().trim();
            boolean suspected = lecturers.existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(name, request.getDateOfBirth());
            String code = codes.generate();
            Long userId = identity.createLecturerAccountWithoutNotification(
                    new CreateLecturerAccountCommand(code, email, INITIAL_PASSWORD));
            Lecturer lecturer = new Lecturer();
            lecturer.setLecturerCode(code);
            lecturer.setUserId(userId);
            lecturer.setFaculty(faculty);
            lecturer.setFullName(name);
            lecturer.setEmail(email);
            lecturer.setCitizenId(citizenId);
            lecturer.setDateOfBirth(request.getDateOfBirth());
            lecturer.setPhone(request.getPhone() == null ? null : request.getPhone().trim());
            lecturer.setStatus(LecturerStatus.ACTIVE);
            lecturer.setAccountNotificationStatus(AccountNotificationStatus.PENDING);
            lecturers.saveAndFlush(lecturer);
            audit(actor, "CREATE_LECTURER", lecturer.getId(), "code=" + code + ",facultyId=" + faculty.getId() + ",reason=" + reason);
            return new Created(lecturer.getId(), suspected ? List.of("Trùng họ tên và ngày sinh; cần kiểm tra thủ công") : List.of());
        });
        if (created == null) throw new BaseException(ErrorCode.UNEXPECTED_ERROR);
        var notification = notifyAccount(created.id(), actor, "CREATE_LECTURER_NOTIFICATION");
        return new CreateLecturerResponse(queries.findById(created.id()), created.warnings(),
                notification.notificationStatus(), notification.notificationError());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LecturerNotificationResponse resend(Long id, String token) {
        return notifyAccount(id, identity.requireAcademicAdmin(token), "RESEND_LECTURER_NOTIFICATION");
    }

    private LecturerNotificationResponse notifyAccount(Long id, String actor, String action) {
        Lecturer lecturer = lecturers.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.LECTURER_NOT_FOUND));
        if (lecturer.getUserId() == null) throw new BaseException(ErrorCode.USER_NOT_FOUND);
        var result = identity.sendLecturerAccountNotification(lecturer.getUserId(), INITIAL_PASSWORD);
        var status = result.emailSent() ? AccountNotificationStatus.SENT : AccountNotificationStatus.FAILED;
        transactions.executeWithoutResult(tx -> {
            Lecturer current = lecturers.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new BaseException(ErrorCode.LECTURER_NOT_FOUND));
            current.setAccountNotificationStatus(status);
            current.setAccountNotificationError(result.emailError());
            lecturers.save(current);
            audit(actor, action, id, "status=" + status + ",error=" + result.emailError());
        });
        return new LecturerNotificationResponse(id, lecturer.getLecturerCode(), status.name(), result.emailError());
    }

    private void audit(String actor, String action, Long id, String details) {
        AuditLog log = new AuditLog();
        log.setActor(actor); log.setAction(action); log.setEntityType("LECTURER");
        log.setEntityId(id.toString()); log.setDetails(details); audits.save(log);
    }

    private record Created(Long id, List<String> warnings) {}
}
