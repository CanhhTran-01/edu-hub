package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.identity.api.dto.request.CreateStudentAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.CreateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.request.StudentSearchRequest;
import com.eduhub.edu_hub.people.api.dto.response.CreateStudentResponse;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.api.mapper.StudentResponseMapper;
import com.eduhub.edu_hub.people.application.code.StudentCodeGenerator;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.AdministrativeClassRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentApplicationService {
    private final StudentRepository studentRepository;
    private final CurriculumFacade.Majors majors;
    private final AdministrativeClassRepository administrativeClassRepository;
    private final CurriculumFacade.ReferenceData curriculum;
    private final StudentCodeGenerator studentCodeGenerator;
    private final IdentityFacade identityFacade;
    private final TransactionTemplate transactionTemplate;
    private final StudentResponseMapper studentResponseMapper;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateStudentResponse create(CreateStudentRequest request) {

        CreatedStudent created = transactionTemplate.execute(status -> createInTransaction(request));
        if (created == null) {
            throw new BaseException(ErrorCode.UNEXPECTED_ERROR);
        }

        // TODO(SINGLE_STUDENT_NOTIFICATION_QUEUE): UC thêm đơn lẻ hiện chỉ gửi lần đầu ở đây.
        //  Khi tích hợp hàng đợi thông báo dùng chung, đưa cả luồng này vào scheduler với 3 retry.
        //  Không khôi phục endpoint resend hoặc reset bộ đếm để vượt giới hạn của luồng nhập học.

        // TODO(NOTIFICATION_STORAGE): Hiện chỉ trả kết quả lần gửi này trong response, không lưu vào Student
        // và không tra cứu lại được sau thao tác. Khi có nhu cầu theo dõi/retry, mở rộng nguồn Notification
        // trước khi tích hợp; không tạo StudentAdmission giả và không thể khôi phục các lần gửi chưa lưu.

        AccountNotificationResponse notification = created.hasEmail()
                ? identityFacade.sendStudentAccountNotification(created.userId())
                : new AccountNotificationResponse(created.userId(), false, "ACCOUNT_EMAIL_MISSING");

        AccountNotificationStatus notificationStatus = !created.hasEmail()
                ? AccountNotificationStatus.SKIPPED
                : notification.emailSent() ? AccountNotificationStatus.SENT : AccountNotificationStatus.FAILED;

        StudentResponse student = findById(created.studentId());
        return new CreateStudentResponse(
                student, created.warnings(), notificationStatus.name(), notification.emailError());
    }

    private CreatedStudent createInTransaction(CreateStudentRequest request) {

        String fullName = request.getFullName().trim();
        String email = TextsNormalizer.normalizeEmail(request.getEmail());
        String citizenId = normalizeCitizenId(request.getCitizenId());

        String reason = request.getReason().trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }

        if (citizenId != null && studentRepository.existsByCitizenIdAndDeletedFalse(citizenId)) {
            throw new BaseException(ErrorCode.DUPLICATE_STUDENT_CITIZEN_ID);
        }

        AdministrativeClass administrativeClass = administrativeClassRepository
                .findByIdForStudentCodeUpdate(request.getAdministrativeClassId())
                .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));
        validateClassScope(request, administrativeClass);
        if (studentRepository.countByAdministrativeClass_IdAndDeletedFalse(administrativeClass.getId())
                >= administrativeClass.getMaxCapacity()) {
            throw new BaseException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
        }

        int sequence = studentCodeGenerator.nextSequence(administrativeClass);
        String studentCode = studentCodeGenerator.generate(administrativeClass, sequence);
        if (studentRepository.existsByStudentCode(studentCode) || identityFacade.existsByUsername(studentCode)) {
            throw new BaseException(ErrorCode.DUPLICATE_STUDENT_CODE);
        }

        boolean possibleDuplicate = request.getDateOfBirth() != null
                && studentRepository.existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(
                        fullName, request.getDateOfBirth());
        Long userId = identityFacade.createStudentAccountWithoutNotification(
                new CreateStudentAccountRequest(studentCode, email));
        Student student = new Student();
        student.setUserId(userId);
        student.setAdministrativeClass(administrativeClass);
        student.setStudentCode(studentCode);
        student.setFullName(fullName);
        student.setDateOfBirth(request.getDateOfBirth());
        student.setGender(
                request.getGender() == null ? null : request.getGender().trim());
        student.setPhone(request.getPhone() == null ? null : request.getPhone().trim());
        student.setCitizenId(citizenId);
        student.setEntranceScore(request.getEntranceScore());
        student.setActivityStatus(ActivityStatus.STUDYING);
        Student saved = studentRepository.save(student);

        List<String> warnings = new ArrayList<>();
        if (possibleDuplicate) {
            warnings.add("Có hồ sơ khác trùng họ tên và ngày sinh; cần kiểm tra thủ công");
        }
        // TODO(AUDIT_LOG): Bổ sung lại nhật ký tạo sinh viên sau.
        return new CreatedStudent(saved.getId(), userId, List.copyOf(warnings), email != null);
    }

    private void validateClassScope(CreateStudentRequest request, AdministrativeClass administrativeClass) {
        majors.requireMajor(request.getMajorId());
        if (administrativeClass.getMajor() == null
                || !administrativeClass.getMajor().getId().equals(request.getMajorId())
                || administrativeClass.getCohort() == null
                || !administrativeClass.getCohort().getId().equals(request.getCohortId())
                || administrativeClass.getTrainingType() != request.getTrainingType()) {
            throw new BaseException(ErrorCode.STUDENT_CLASS_MISMATCH);
        }
    }

    private String normalizeCitizenId(String citizenId) {

        if (citizenId == null) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }

        String normalized = citizenId.replaceAll("\\s+", "");

        if (!normalized.matches("[0-9]{12}")) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }

        return normalized;
    }

    private record CreatedStudent(Long studentId, Long userId, List<String> warnings, boolean hasEmail) {}

    public PageResponse<StudentResponse> search(StudentSearchRequest request, int page, int size) {
        ActivityStatus status = null;
        if (request.getActivityStatus() != null && !request.getActivityStatus().isBlank()) {
            try {
                status = ActivityStatus.valueOf(request.getActivityStatus());
            } catch (IllegalArgumentException exception) {
                throw new BaseException(ErrorCode.VALIDATION_ERROR);
            }
        }
        ActivityStatus selectedStatus = status;
        Specification<Student> filters = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deleted")));

            String keyword = request.getKeyword();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("studentCode")), pattern),
                        builder.like(builder.lower(root.get("fullName")), pattern)));
            }

            if (request.getClassId() != null || request.getMajorId() != null || request.getCohortId() != null) {
                Join<Student, AdministrativeClass> administrativeClass =
                        root.join("administrativeClass", JoinType.LEFT);
                if (request.getClassId() != null) {
                    predicates.add(builder.equal(administrativeClass.get("id"), request.getClassId()));
                }
                if (request.getMajorId() != null) {
                    predicates.add(
                            builder.equal(administrativeClass.get("major").get("id"), request.getMajorId()));
                }
                if (request.getCohortId() != null) {
                    Join<AdministrativeClass, Cohort> cohort = administrativeClass.join("cohort", JoinType.LEFT);
                    predicates.add(builder.equal(cohort.get("id"), request.getCohortId()));
                }
            }

            if (selectedStatus != null) {
                predicates.add(builder.equal(root.get("activityStatus"), selectedStatus));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        var students = studentRepository.findAll(
                filters, PageRequest.of(page, size, Sort.by("id").ascending()));
        var majorNames = majorNames(students.getContent());
        var accountEmails = accountEmailsByUserId(students.getContent());
        return PageMapper.toPageResponse(students.map(student -> toResponse(student, majorNames, accountEmails)));
    }

    public StudentResponse findById(Long id) {
        return studentRepository
                .findByIdAndDeletedFalse(id)
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
    }

    private StudentResponse toResponse(Student student) {
        return toResponse(student, majorNames(List.of(student)), accountEmailsByUserId(List.of(student)));
    }

    private Map<Long, String> accountEmailsByUserId(List<Student> students) {
        var userIds = students.stream()
                .map(Student::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        // Một lần gọi facade cho cả trang; email null/không có tài khoản đều hiển thị null, không đọc User entity.
        return identityFacade.findAccountEmailsByUserIds(userIds).stream()
                .filter(account -> account.email() != null)
                .collect(Collectors.toMap(UserAccountEmailResponse::userId, UserAccountEmailResponse::email));
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

    private StudentResponse toResponse(
            Student student, Map<Long, String> majorNames, Map<Long, String> accountEmailsByUserId) {
        String accountEmail = student.getUserId() == null ? null : accountEmailsByUserId.get(student.getUserId());
        return studentResponseMapper.toStudentResponse(student, majorNames, accountEmail);
    }
}
