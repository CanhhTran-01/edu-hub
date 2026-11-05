package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.application.command.CreateStudentAccountCommand;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.application.result.CreateStudentAccountResult;
import com.eduhub.edu_hub.people.api.dto.request.AdmissionClassAdjustmentRequest;
import com.eduhub.edu_hub.people.api.dto.request.AdmissionConfirmRequest;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionClassPreview;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionConfirmResponse;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionErrorResponse;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionPreviewResponse;
import com.eduhub.edu_hub.people.api.dto.response.AdmissionStudentPreview;
import com.eduhub.edu_hub.people.application.admission.AdmissionBatchState;
import com.eduhub.edu_hub.people.application.admission.AdmissionCandidate;
import com.eduhub.edu_hub.people.application.admission.AdmissionParseResult;
import com.eduhub.edu_hub.people.application.admission.AdmissionPreviewStore;
import com.eduhub.edu_hub.people.application.admission.ClassAssignmentStrategy;
import com.eduhub.edu_hub.people.application.admission.StudentAdmissionSpreadsheetParser;
import com.eduhub.edu_hub.people.application.code.StudentCodeGenerator;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.AdministrativeClassRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class StudentAdmissionService {
    private final StudentAdmissionSpreadsheetParser parser;
    private final ClassAssignmentStrategy assignmentStrategy;
    private final AdmissionPreviewStore previewStore;
    private final StudentRepository students;
    private final AdministrativeClassRepository classes;
    private final StudentCodeGenerator codeGenerator;
    private final IdentityFacade identityFacade;
    private final CurriculumFacade.Majors majors;

    private static final String INITIAL_PASSWORD = "12345";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public AdmissionPreviewResponse preview(MultipartFile file) {

        // Validate the uploaded file
        if (file == null
                || file.isEmpty()
                || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }

        AdmissionParseResult parsed;
        try {
            parsed = parser.parse(file.getInputStream());
        } catch (IOException exception) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }

        List<AdmissionCandidate> valid = validate(parsed.candidates(), parsed.errors());
        List<AdministrativeClass> eligible = eligibleClasses(valid, parsed.errors());

        var states = eligible.stream()
                .map(value -> new ClassAssignmentStrategy.ClassState(
                        value, students.findAllByAdministrativeClass_IdAndDeletedFalse(value.getId())))
                .toList();

        ClassAssignmentStrategy.AssignmentResult assignment = assignmentStrategy.assign(valid, states);

        valid.stream().filter(value -> value.getAssignedClassId() == null).forEach(value -> parsed.errors()
                .add(new AdmissionErrorResponse(
                        value.getRowNumber(),
                        value.getApplicationCode(),
                        "lop",
                        "Không còn lớp phù hợp trong sức chứa tối đa")));

        valid.removeIf(value -> value.getAssignedClassId() == null);

        UUID batchId = UUID.randomUUID();
        AdmissionBatchState batch = new AdmissionBatchState(
                batchId,
                safeFileName(file.getOriginalFilename()),
                parsed.totalRows(),
                valid,
                parsed.errors(),
                eligible,
                assignment.duplicateNameWarnings(),
                assignment.capacityWarnings());

        previewStore.put(batch);
        return response(batch);
    }

    public AdmissionPreviewResponse adjust(UUID batchId, AdmissionClassAdjustmentRequest request) {

        AdmissionBatchState batch = previewStore.get(batchId);

        synchronized (batch) {
            ensureOpen(batch);

            AdmissionCandidate candidate = batch.getCandidates().stream()
                    .filter(value -> value.getApplicationCode().equalsIgnoreCase(request.applicationCode()))
                    .findFirst()
                    .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));

            AdministrativeClass target = batch.getClasses().stream()
                    .filter(value -> value.getId().equals(request.targetClassId()))
                    .findFirst()
                    .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));

            long projected = students.findAllByAdministrativeClass_IdAndDeletedFalse(target.getId())
                            .size()
                    + batch.getCandidates().stream()
                            .filter(value -> target.getId().equals(value.getAssignedClassId()))
                            .count();

            if (!target.getId().equals(candidate.getAssignedClassId()) && projected >= target.getMaxCapacity()) {
                throw new BaseException(ErrorCode.CLASS_CAPACITY_EXCEEDED);
            }

            candidate.setAssignedClassId(target.getId());

            // TODO(AUDIT_LOG): Bổ sung lại nhật ký điều chỉnh phân lớp sau.

            recalculateWarnings(batch);
            return response(batch);
        }
    }

    @Transactional
    public AdmissionConfirmResponse confirm(UUID batchId, AdmissionConfirmRequest request) {

        AdmissionBatchState batch = previewStore.get(batchId);

        synchronized (batch) {
            ensureOpen(batch);

            if (!batch.getCapacityWarnings().isEmpty() && !request.acceptMinimumCapacityWarning()) {
                throw new BaseException(ErrorCode.ADMISSION_CAPACITY_CONFIRMATION_REQUIRED);
            }

            Map<Long, AdministrativeClass> byId = lockAssignedClasses(batch);
            Map<Long, Integer> nextSequenceByClass = new java.util.HashMap<>();

            List<String> generatedCodes = batch.getCandidates().stream()
                    .map(candidate -> {
                        AdministrativeClass assignedClass = byId.get(candidate.getAssignedClassId());

                        int sequence = nextSequenceByClass.compute(
                                assignedClass.getId(),
                                (classId, current) ->
                                        current == null ? codeGenerator.nextSequence(assignedClass) : current + 1);

                        return codeGenerator.generate(assignedClass, sequence);
                    })
                    .toList();

            if (generatedCodes.size() != new HashSet<>(generatedCodes).size()
                    || generatedCodes.stream().anyMatch(students::existsByStudentCode)) {
                throw new BaseException(ErrorCode.DUPLICATE_STUDENT_CODE);
            }

            List<String> emailFailures = new ArrayList<>();

            for (int index = 0; index < batch.getCandidates().size(); index++) {

                AdmissionCandidate candidate = batch.getCandidates().get(index);
                String studentCode = generatedCodes.get(index);

                CreateStudentAccountResult account = identityFacade.createStudentAccount(
                        new CreateStudentAccountCommand(studentCode, candidate.getEmail(), INITIAL_PASSWORD, true));

                Student student = new Student();
                student.setUserId(account.userId());
                student.setAdministrativeClass(byId.get(candidate.getAssignedClassId()));
                student.setStudentCode(studentCode);
                student.setAdmissionApplicationCode(candidate.getApplicationCode());
                student.setFullName(candidate.getFullName());
                student.setDateOfBirth(candidate.getDateOfBirth());
                student.setGender(candidate.getGender());
                student.setEmail(candidate.getEmail());
                student.setEntranceScore(candidate.getEntranceScore());
                student.setActivityStatus(ActivityStatus.STUDYING);

                students.save(student);
                if (!account.emailSent()) emailFailures.add(candidate.getApplicationCode());
            }

            batch.setConfirmed(true);

            // TODO(AUDIT_LOG): Bổ sung lại nhật ký xác nhận nhập học sau.

            return new AdmissionConfirmResponse(
                    batchId, batch.getCandidates().size(), batch.getCandidates().size(), emailFailures);
        }
    }

    private List<AdmissionCandidate> validate(List<AdmissionCandidate> parsed, List<AdmissionErrorResponse> errors) {

        Set<String> codes = new HashSet<>();
        Set<String> emails = new HashSet<>();
        List<AdmissionCandidate> valid = new ArrayList<>();

        String batchMajor = null, batchCohort = null, batchTraining = null;

        for (AdmissionCandidate value : parsed) {

            boolean ok = true;

            String code = value.getApplicationCode().toLowerCase(Locale.ROOT);
            String email = value.getEmail().toLowerCase(Locale.ROOT);

            if (!codes.add(code)) {
                add(errors, value, "ma_ho_so", "Trùng mã hồ sơ trong file");
                ok = false;
            }

            if (!emails.add(email)) {
                add(errors, value, "email", "Trùng email trong file");
                ok = false;
            }
            if (!EMAIL.matcher(value.getEmail()).matches()) {
                add(errors, value, "email", "Email sai định dạng");
                ok = false;
            }

            if (students.existsByAdmissionApplicationCodeAndDeletedFalse(value.getApplicationCode())) {
                add(errors, value, "ma_ho_so", "Mã hồ sơ đã tồn tại");
                ok = false;
            }
            if (students.existsByEmailIgnoreCaseAndDeletedFalse(value.getEmail())) {
                add(errors, value, "email", "Email đã tồn tại");
                ok = false;
            }
            try {
                TrainingType.valueOf(value.getTrainingType().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                add(errors, value, "he_dao_tao", "Hệ đào tạo không hợp lệ");
                ok = false;
            }

            if (batchMajor == null) {
                batchMajor = value.getMajorCode();
                batchCohort = value.getCohortCode();
                batchTraining = value.getTrainingType();

            } else if (!batchMajor.equalsIgnoreCase(value.getMajorCode())
                    || !batchCohort.equalsIgnoreCase(value.getCohortCode())
                    || !batchTraining.equalsIgnoreCase(value.getTrainingType())) {
                add(errors, value, "file", "Mỗi file chỉ được chứa một ngành, một khóa và một hệ đào tạo");
                ok = false;
            }

            if (ok) valid.add(value);
        }
        return valid;
    }

    private List<AdministrativeClass> eligibleClasses(
            List<AdmissionCandidate> valid, List<AdmissionErrorResponse> errors) {

        if (valid.isEmpty()) return List.of();

        AdmissionCandidate first = valid.getFirst();
        TrainingType type = TrainingType.valueOf(first.getTrainingType().toUpperCase(Locale.ROOT));
        var major = majors.findMajorByCode(first.getMajorCode());

        if (major.isEmpty()) {
            valid.forEach(value -> add(errors, value, "ma_nganh", "Major code does not exist in Curriculum"));
            valid.clear();
            return List.of();
        }

        List<AdministrativeClass> result =
                classes.findByMajor_IdAndCohort_CohortCodeIgnoreCaseAndTrainingTypeAndDeletedFalse(
                        major.get().id(), first.getCohortCode(), type);

        if (result.isEmpty()) {
            valid.forEach(
                    value -> add(errors, value, "ma_nganh", "Không có lớp phù hợp với ngành, khóa và hệ đào tạo"));
            valid.clear();
        }

        return result;
    }

    private void recalculateWarnings(AdmissionBatchState batch) {

        List<String> capacity = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();

        for (AdministrativeClass value : batch.getClasses()) {

            List<Student> existing = students.findAllByAdministrativeClass_IdAndDeletedFalse(value.getId());

            List<AdmissionCandidate> assigned = batch.getCandidates().stream()
                    .filter(c -> value.getId().equals(c.getAssignedClassId()))
                    .toList();

            int size = existing.size() + assigned.size();

            if (size < value.getMinCapacity()) {
                capacity.add("Lớp " + value.getClassName() + " dưới sức chứa tối thiểu");
            }

            Set<String> names = existing.stream()
                    .map(Student::getFullName)
                    .map(this::normalizeName)
                    .collect(Collectors.toSet());

            assigned.forEach(candidate -> {
                if (!names.add(normalizeName(candidate.getFullName()))) {
                    duplicates.add("Lớp " + value.getClassName() + " có trùng họ tên: " + candidate.getFullName());
                }
            });
        }

        batch.setCapacityWarnings(capacity);
        batch.setDuplicateNameWarnings(duplicates);
    }

    private AdmissionPreviewResponse response(AdmissionBatchState batch) {
        List<AdmissionClassPreview> classPreviews = batch.getClasses().stream()
                .map(value -> {
                    List<Student> existing = students.findAllByAdministrativeClass_IdAndDeletedFalse(value.getId());

                    List<AdmissionStudentPreview> assigned = batch.getCandidates().stream()
                            .filter(c -> value.getId().equals(c.getAssignedClassId()))
                            .map(c -> studentPreview(c, value))
                            .toList();

                    BigDecimal sum = existing.stream()
                            .map(Student::getEntranceScore)
                            .filter(java.util.Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .add(assigned.stream()
                                    .map(AdmissionStudentPreview::entranceScore)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add));

                    long count = existing.stream()
                                    .map(Student::getEntranceScore)
                                    .filter(java.util.Objects::nonNull)
                                    .count()
                            + assigned.size();

                    BigDecimal average = count == 0
                            ? BigDecimal.ZERO
                            : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

                    return new AdmissionClassPreview(
                            value.getId(),
                            value.getClassName(),
                            existing.size(),
                            existing.size() + assigned.size(),
                            value.getMinCapacity(),
                            value.getMaxCapacity(),
                            average,
                            assigned);
                })
                .toList();

        return new AdmissionPreviewResponse(
                batch.getId(),
                batch.getFileName(),
                batch.getTotalRows(),
                batch.getCandidates().size(),
                batch.getErrors().size(),
                classPreviews,
                List.copyOf(batch.getErrors()),
                List.copyOf(batch.getDuplicateNameWarnings()),
                List.copyOf(batch.getCapacityWarnings()));
    }

    private AdmissionStudentPreview studentPreview(AdmissionCandidate c, AdministrativeClass value) {
        return new AdmissionStudentPreview(
                c.getRowNumber(), c.getApplicationCode(),
                c.getFullName(), c.getDateOfBirth(),
                c.getGender(), c.getEmail(),
                c.getEntranceScore(), c.getMajorCode(),
                c.getCohortCode(), c.getTrainingType(),
                value.getId(), value.getClassName());
    }

    private void ensureOpen(AdmissionBatchState batch) {
        if (batch.isConfirmed()) throw new BaseException(ErrorCode.ADMISSION_BATCH_ALREADY_CONFIRMED);
    }

    private void add(List<AdmissionErrorResponse> errors, AdmissionCandidate value, String field, String reason) {
        errors.add(new AdmissionErrorResponse(value.getRowNumber(), value.getApplicationCode(), field, reason));
    }

    private String normalizeName(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String safeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Map<Long, AdministrativeClass> lockAssignedClasses(AdmissionBatchState batch) {
        return batch.getCandidates().stream()
                .map(AdmissionCandidate::getAssignedClassId)
                .distinct()
                .sorted()
                .map(classId -> classes.findByIdForStudentCodeUpdate(classId)
                        .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR)))
                .collect(Collectors.toMap(AdministrativeClass::getId, Function.identity()));
    }
}
