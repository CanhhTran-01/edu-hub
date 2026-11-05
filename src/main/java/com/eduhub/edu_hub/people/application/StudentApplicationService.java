package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.identity.application.command.CreateStudentAccountCommand;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.application.result.CreateStudentAccountResult;
import com.eduhub.edu_hub.people.api.dto.request.CreateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.request.StudentSearchRequest;
import com.eduhub.edu_hub.people.api.dto.request.UpdateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.people.api.dto.response.CreateStudentResponse;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
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
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StudentApplicationService {
    private static final String INITIAL_PASSWORD = "12345";

    private final StudentRepository studentRepository;
    private final CurriculumFacade.Majors majors;
    private final AdministrativeClassRepository administrativeClassRepository;
    private final CurriculumFacade.ReferenceData curriculum;
    private final StudentCodeGenerator studentCodeGenerator;
    private final IdentityFacade identityFacade;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateStudentResponse create(CreateStudentRequest request, String accessToken) {
        identityFacade.requireAcademicAdmin(accessToken);
        CreatedStudent created = transactionTemplate.execute(status -> createInTransaction(request));
        if (created == null) {
            throw new BaseException(ErrorCode.UNEXPECTED_ERROR);
        }

        CreateStudentAccountResult notification =
                identityFacade.sendStudentAccountNotification(created.userId(), INITIAL_PASSWORD);

        AccountNotificationStatus notificationStatus =
                notification.emailSent() ? AccountNotificationStatus.SENT : AccountNotificationStatus.FAILED;

        recordNotificationResult(created.studentId(), notificationStatus, notification.emailError());

        StudentResponse student = findById(created.studentId());
        return new CreateStudentResponse(
                student, created.warnings(), notificationStatus.name(), notification.emailError());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AccountNotificationResponse resendAccountNotification(Long studentId, String accessToken) {
        identityFacade.requireAcademicAdmin(accessToken);
        Student student = studentRepository
                .findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
        if (student.getUserId() == null) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }

        CreateStudentAccountResult notification =
                identityFacade.sendStudentAccountNotification(student.getUserId(), INITIAL_PASSWORD);
        AccountNotificationStatus notificationStatus =
                notification.emailSent() ? AccountNotificationStatus.SENT : AccountNotificationStatus.FAILED;
        recordNotificationResult(studentId, notificationStatus, notification.emailError());
        return new AccountNotificationResponse(
                studentId, student.getStudentCode(), notificationStatus.name(), notification.emailError());
    }

    private CreatedStudent createInTransaction(CreateStudentRequest request) {
        String fullName = request.getFullName().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String citizenId = normalizeCitizenId(request.getCitizenId());
        String reason = request.getReason().trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }

        if (studentRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
            throw new BaseException(ErrorCode.DUPLICATE_STUDENT_EMAIL);
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
                new CreateStudentAccountCommand(studentCode, email, INITIAL_PASSWORD, true));
        Student student = new Student();
        student.setUserId(userId);
        student.setAdministrativeClass(administrativeClass);
        student.setStudentCode(studentCode);
        student.setFullName(fullName);
        student.setDateOfBirth(request.getDateOfBirth());
        student.setGender(
                request.getGender() == null ? null : request.getGender().trim());
        student.setEmail(email);
        student.setPhone(request.getPhone() == null ? null : request.getPhone().trim());
        student.setCitizenId(citizenId);
        student.setEntranceScore(request.getEntranceScore());
        student.setActivityStatus(ActivityStatus.STUDYING);
        student.setAccountNotificationStatus(AccountNotificationStatus.PENDING);
        student.setAccountNotificationError(null);
        Student saved = studentRepository.save(student);

        List<String> warnings = new ArrayList<>();
        if (possibleDuplicate) {
            warnings.add("Có hồ sơ khác trùng họ tên và ngày sinh; cần kiểm tra thủ công");
        }
        // TODO(AUDIT_LOG): Bổ sung lại nhật ký tạo sinh viên sau.
        return new CreatedStudent(saved.getId(), userId, List.copyOf(warnings));
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
        if (citizenId == null) return null;
        String normalized = citizenId.replaceAll("\\s+", "");
        return normalized.isBlank() ? null : normalized;
    }

    private void recordNotificationResult(Long studentId, AccountNotificationStatus status, String error) {

        TransactionTemplate updateTemplate =
                new TransactionTemplate(Objects.requireNonNull(transactionTemplate.getTransactionManager()));

        updateTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        updateTemplate.executeWithoutResult(transactionStatus -> {
            Student student = studentRepository
                    .findByIdAndDeletedFalse(studentId)
                    .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
            student.setAccountNotificationStatus(status);
            student.setAccountNotificationError(error);
            studentRepository.save(student);

            // TODO(AUDIT_LOG): Bổ sung lại nhật ký gửi/gửi lại thông báo tài khoản sinh viên sau.
        });
    }

    private record CreatedStudent(Long studentId, Long userId, List<String> warnings) {}

    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest request) {
        if (request.getStudentCode() != null) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }
        Student student = studentRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
        if (request.getFullName() != null) {
            if (request.getFullName().isBlank()) {
                throw new BaseException(ErrorCode.VALIDATION_ERROR);
            }
            student.setFullName(request.getFullName().trim());
        }
        if (request.getDateOfBirth() != null) student.setDateOfBirth(request.getDateOfBirth());
        if (request.getEmail() != null) student.setEmail(request.getEmail());
        if (request.getPhone() != null) student.setPhone(request.getPhone());
        if (request.getCitizenId() != null) student.setCitizenId(request.getCitizenId());
        if (request.getTargetGpa() != null) student.setTargetGpa(request.getTargetGpa());
        updateAcademicWarning(student, request);
        if (request.getAdministrativeClassId() != null) {
            AdministrativeClass administrativeClass = administrativeClassRepository
                    .findByIdAndDeletedFalse(request.getAdministrativeClassId())
                    .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));
            student.setAdministrativeClass(administrativeClass);
        }
        StudentResponse response = toResponse(studentRepository.save(student));
        log.info("Updated student profile: studentId={}, reason={}", id, request.getStatusReason());
        return response;
    }

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
            if (request.getAcademicWarningStatus() != null
                    && !request.getAcademicWarningStatus().isBlank()) {
                predicates.add(builder.equal(root.get("academicWarningStatus"), request.getAcademicWarningStatus()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        var students = studentRepository.findAll(
                filters, PageRequest.of(page, size, Sort.by("id").ascending()));
        var majorNames = majorNames(students.getContent());
        return PageMapper.toPageResponse(students.map(student -> toResponse(student, majorNames)));
    }

    public StudentResponse findById(Long id) {
        return studentRepository
                .findByIdAndDeletedFalse(id)
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_NOT_FOUND));
    }

    private StudentResponse toResponse(Student student) {
        return toResponse(student, majorNames(List.of(student)));
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

    private void updateAcademicWarning(Student student, UpdateStudentRequest request) {
        if (request.getAcademicWarningStatus() != null) {
            String status = normalizeOptionalText(request.getAcademicWarningStatus());
            if (!Objects.equals(status, student.getAcademicWarningStatus())) {
                student.setAcademicWarningReason(null);
            }
            student.setAcademicWarningStatus(status);
            if (status == null) {
                student.setAcademicWarningReason(null);
                return;
            }
        }
        if (request.getAcademicWarningReason() != null) {
            String reason = normalizeOptionalText(request.getAcademicWarningReason());
            if (reason != null && normalizeOptionalText(student.getAcademicWarningStatus()) == null) {
                throw new BaseException(ErrorCode.VALIDATION_ERROR);
            }
            student.setAcademicWarningReason(reason);
        }
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private StudentResponse toResponse(Student student, Map<Long, String> majorNames) {
        AdministrativeClass administrativeClass = student.getAdministrativeClass();
        return StudentResponse.builder()
                .id(student.getId())
                .studentCode(student.getStudentCode())
                .fullName(student.getFullName())
                .dateOfBirth(student.getDateOfBirth())
                .email(student.getEmail())
                .phone(student.getPhone())
                .citizenId(student.getCitizenId())
                .className(administrativeClass == null ? null : administrativeClass.getClassName())
                .majorName(
                        administrativeClass == null || administrativeClass.getMajor() == null
                                ? null
                                : majorNames.get(administrativeClass.getMajor().getId()))
                .cohortCode(
                        administrativeClass == null
                                ? null
                                : administrativeClass.getCohort().getCohortCode())
                .trainingType(
                        administrativeClass == null
                                ? null
                                : administrativeClass.getTrainingType().name())
                .entranceScore(student.getEntranceScore())
                .targetGpa(student.getTargetGpa())
                .activityStatus(student.getActivityStatus())
                .academicWarningStatus(student.getAcademicWarningStatus())
                .academicWarningReason(student.getAcademicWarningReason())
                .createdAt(student.getCreatedAt())
                .build();
    }
}
