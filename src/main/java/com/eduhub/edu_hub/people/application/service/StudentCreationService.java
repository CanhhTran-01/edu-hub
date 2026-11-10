package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.api.dto.request.CreateStudentAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.CreateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.response.CreateStudentResponse;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.api.mapper.StudentResponseMapper;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.policy.ProfileCreationPolicy;
import com.eduhub.edu_hub.people.domain.repository.AdministrativeClassRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Tạo sinh viên đơn lẻ ngoài đợt nhập học; cùng bộ cấp MSSV và tài khoản với nhập học hàng loạt. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentCreationService {
    private final StudentRepository studentRepository;
    private final AdministrativeClassRepository administrativeClassRepository;
    private final CurriculumFacade.Majors curriculumMajors;
    private final StudentCodeGenerator studentCodeGenerator;
    private final IdentityFacade identityFacade;
    private final TransactionTemplate transactionTemplate;
    private final StudentResponseMapper studentResponseMapper;

    private record CreatedStudent(
            StudentResponse student, Long userId, List<String> warnings, boolean hasEmail, String reason) {}

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateStudentResponse createStudent(Jwt authenticatedJwt, CreateStudentRequest request) {
        if (authenticatedJwt == null) throw new ProfileCreationException(ErrorCode.UNAUTHENTICATED);

        // TODO(AUTHORIZATION): Khi triển khai phân quyền, giới hạn tạo hồ sơ đơn lẻ cho ACADEMIC_ADMIN.
        var currentUser = identityFacade.requireCurrentUser(authenticatedJwt);

        CreatedStudent createdStudent = transactionTemplate.execute(transactionStatus -> createInTransaction(request));
        if (createdStudent == null) throw new ProfileCreationException(ErrorCode.UNEXPECTED_ERROR);

        // TODO(AUDIT_LOG): Ghi actor, Student ID, MSSV, lý do và kết quả sau commit vào audit trail khi có hạ tầng.
        log.info(
                "Created student: actorId={}, studentId={}, studentCode={}, reason={}",
                currentUser.getId(),
                createdStudent.student().getId(),
                createdStudent.student().getStudentCode(),
                createdStudent.reason());

        // TODO(NOTIFICATION_DELIVERY): Hiện tại chỉ gửi đồng bộ một lần sau commit,
        //  không lưu trạng thái/lịch sử, không có resend/retry.
        //  Gửi lỗi hoặc tiến trình dừng giữa commit và gửi thì tài khoản vẫn tồn tại nhưng có thể chưa nhận email.
        AccountNotificationResponse notification = sendAccountEmail(createdStudent);

        if (createdStudent.hasEmail() && !notification.emailSent()) {
            log.warn("Student account email was not sent: userId={}, errorCode={}",
                    createdStudent.userId(), notification.emailError());
        }

        AccountNotificationStatus notificationStatus = !createdStudent.hasEmail()
                ? AccountNotificationStatus.SKIPPED
                : notification.emailSent() ? AccountNotificationStatus.SENT : AccountNotificationStatus.FAILED;
        return new CreateStudentResponse(
                createdStudent.student(),
                createdStudent.warnings(),
                notificationStatus.name(),
                notification.emailError());
    }

    private CreatedStudent createInTransaction(CreateStudentRequest request) {

        String fullName = request.getFullName().trim();
        String email = TextsNormalizer.normalizeEmail(request.getEmail());
        String reason = ProfileCreationPolicy.normalizeReason(request.getReason());

        String citizenId = ProfileCreationPolicy.normalizeRequiredCitizenId(request.getCitizenId());
        if (studentRepository.existsByCitizenIdAndDeletedFalse(citizenId)) {
            throw new ProfileCreationException(
                    ErrorCode.DUPLICATE_STUDENT_CITIZEN_ID, "citizenId", "CCCD sinh viên đã tồn tại");
        }

        // Khóa lớp trước kiểm tra sức chứa/cấp số; bộ đếm, User và Student phải cùng commit hoặc rollback.
        AdministrativeClass administrativeClass = administrativeClassRepository
                .findByIdForStudentCodeUpdate(request.getAdministrativeClassId())
                .orElseThrow(() -> new ProfileCreationException(
                        ErrorCode.VALIDATION_ERROR,
                        "administrativeClassId",
                        "Lớp hành chính không tồn tại hoặc đã bị xóa"));
        var major = curriculumMajors.requireMajor(request.getMajorId());
        ProfileCreationPolicy.validateStudentClassScope(
                administrativeClass, request.getMajorId(), request.getCohortId(), request.getTrainingType());
        if (studentRepository.countByAdministrativeClass_IdAndDeletedFalse(administrativeClass.getId())
                >= administrativeClass.getMaxCapacity()) {
            throw new ProfileCreationException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
        }
        int studentSequence = studentCodeGenerator.allocateNextStudentSequence(administrativeClass);
        String studentCode = studentCodeGenerator.generateStudentCode(administrativeClass, studentSequence);
        if (studentRepository.existsByStudentCode(studentCode) || identityFacade.existsByUsername(studentCode)) {
            throw new ProfileCreationException(ErrorCode.DUPLICATE_STUDENT_CODE);
        }

        boolean hasPossibleDuplicate = request.getDateOfBirth() != null
                && studentRepository.existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(
                        fullName, request.getDateOfBirth());
        Long userId = identityFacade.createStudentAccountWithoutNotification(
                new CreateStudentAccountRequest(studentCode, email));

        Student student = Student.builder()
                .userId(userId)
                .administrativeClass(administrativeClass)
                .studentCode(studentCode)
                .fullName(fullName)
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender() == null ? null : request.getGender().trim())
                .phone(request.getPhone() == null ? null : request.getPhone().trim())
                .citizenId(citizenId)
                .entranceScore(request.getEntranceScore())
                .activityStatus(ActivityStatus.STUDYING)
                .build();

        Student savedStudent = studentRepository.save(student);

        List<String> warnings = hasPossibleDuplicate
                ? List.of("Có hồ sơ khác trùng họ tên và ngày sinh; cần kiểm tra thủ công")
                : List.of();

        StudentResponse studentResponse = studentResponseMapper.toStudentResponse(
                savedStudent, Map.of(request.getMajorId(), major.majorName()), email);
        return new CreatedStudent(studentResponse, userId, warnings, email != null, reason);
    }

    private AccountNotificationResponse sendAccountEmail(CreatedStudent createdStudent) {
        if (!createdStudent.hasEmail())
            return new AccountNotificationResponse(createdStudent.userId(), false, "ACCOUNT_EMAIL_MISSING");
        try {
            return identityFacade.sendStudentAccountNotification(createdStudent.userId());
        } catch (RuntimeException exception) {
            log.warn(
                    "Student account email failed: userId={}, failureType={}",
                    createdStudent.userId(),
                    exception.getClass().getSimpleName());
            return new AccountNotificationResponse(createdStudent.userId(), false, "EMAIL_SEND_FAILED");
        }
    }
}
