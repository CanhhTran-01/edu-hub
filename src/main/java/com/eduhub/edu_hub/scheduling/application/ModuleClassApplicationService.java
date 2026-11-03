package com.eduhub.edu_hub.scheduling.application;

import com.eduhub.edu_hub.curriculum.api.dto.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.CurriculumFacade;
import com.eduhub.edu_hub.scheduling.api.dto.*;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassConflictException;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassOpeningException;
import com.eduhub.edu_hub.scheduling.application.port.AcademicPlanningDataSource;
import com.eduhub.edu_hub.scheduling.application.port.ModuleClassEnrollmentQuery;
import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot;
import com.eduhub.edu_hub.scheduling.application.specification.ModuleClassSpecification;
import com.eduhub.edu_hub.scheduling.domain.entity.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.entity.Semester;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.policy.ModuleClassPolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ModuleClassApplicationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;
    private final CurriculumFacade.ClassOpening curriculumFacade;
    private final AcademicPlanningDataSource planningDataSource;
    private final ModuleClassEnrollmentQuery enrollmentQuery;
    private final OpeningDemandCalculator demandCalculator;
    private final Clock clock;

    @Autowired
    public ModuleClassApplicationService(
            SemesterRepository semesterRepository,
            ModuleClassRepository moduleClassRepository,
            CurriculumFacade.ClassOpening curriculumFacade,
            AcademicPlanningDataSource planningDataSource,
            ModuleClassEnrollmentQuery enrollmentQuery,
            OpeningDemandCalculator demandCalculator) {
        this(
                semesterRepository,
                moduleClassRepository,
                curriculumFacade,
                planningDataSource,
                enrollmentQuery,
                demandCalculator,
                Clock.system(BUSINESS_ZONE));
    }

    ModuleClassApplicationService(
            SemesterRepository semesterRepository,
            ModuleClassRepository moduleClassRepository,
            CurriculumFacade.ClassOpening curriculumFacade,
            AcademicPlanningDataSource planningDataSource,
            ModuleClassEnrollmentQuery enrollmentQuery,
            OpeningDemandCalculator demandCalculator,
            Clock clock) {
        this.semesterRepository = semesterRepository;
        this.moduleClassRepository = moduleClassRepository;
        this.curriculumFacade = curriculumFacade;
        this.planningDataSource = planningDataSource;
        this.enrollmentQuery = enrollmentQuery;
        this.demandCalculator = demandCalculator;
        this.clock = clock.withZone(BUSINESS_ZONE);
    }

    @Transactional(readOnly = true)
    public List<OpeningSubjectResponse> findOpeningSubjects(Long semesterId) {
        Semester semester = semesterRepository
                .findByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new BaseException(ErrorCode.SEMESTER_NOT_FOUND));

        requireOpenSemester(semester);

        var data = planningDataSource.snapshot();

        var subjects =
                demandCalculator.calculate(semester.getSemesterCode(), curriculumFacade.findPlanningSubjects(), data);

        var existing = nonCancelledCounts(moduleClassRepository.findAllBySemester_IdOrderByIdAsc(semesterId));

        subjects.forEach(subject -> subject.setExistingClassCount(existing.getOrDefault(subject.getSubjectId(), 0L)));
        return subjects;
    }

    @Transactional
    public List<ModuleClassResponse> createBatch(Long semesterId, CreateModuleClassesRequest request) {

        Set<Long> subjectIds = validateBatch(request);

        // Mọi luồng mở lớp/cập nhật kỳ phải cùng khóa hàng kỳ trước khi kiểm tra và cấp STT.
        Semester semester = semesterRepository
                .findForUpdateByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new BaseException(ErrorCode.SEMESTER_NOT_FOUND));

        requireOpenSemester(semester);

        var subjects = indexSubjects(curriculumFacade.requireOpenableSubjects(subjectIds));

        // Bao gồm lớp hủy/soft delete để không bao giờ tái sử dụng STT đã cấp.
        List<ModuleClass> history = moduleClassRepository.findAllBySemester_IdOrderByIdAsc(semesterId);
        var existing = nonCancelledCounts(history);
        var warnings = new ArrayList<FieldErrorDetail>();

        for (int i = 0; i < request.getSubjects().size(); i++) {
            var item = request.getSubjects().get(i);
            long count = existing.getOrDefault(item.getSubjectId(), 0L);
            if (count > 0 && !item.isConfirmAdditionalClasses()) {
                warnings.add(new FieldErrorDetail(
                        "subjects[" + i + "].subjectId",
                        subjects.get(item.getSubjectId()).getSubjectCode() + " đã có " + count
                                + " lớp chưa hủy trong kỳ; gửi confirmAdditionalClasses=true để mở thêm"));
            }
        }
        if (!warnings.isEmpty())
            throw new ModuleClassOpeningException(ErrorCode.MODULE_CLASS_CONFIRMATION_REQUIRED, warnings);

        Map<Long, Integer> sequences = new HashMap<>();
        history.forEach(item -> sequences.merge(item.getSubjectId(), item.getClassSequence(), Math::max));
        var created = new ArrayList<ModuleClass>();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        for (var subject : request.getSubjects()) {
            for (var item : subject.getClasses()) {
                int sequence = sequences.merge(subject.getSubjectId(), 1, Integer::sum);
                String code = String.format(
                        Locale.ROOT,
                        "MC_%d_%s_%03d",
                        semesterId,
                        subjects.get(subject.getSubjectId()).getSubjectCode(),
                        sequence);
                var moduleClass = ModuleClass.builder()
                        .semester(semester)
                        .subjectId(subject.getSubjectId())
                        .moduleClassCode(code)
                        .classSequence(sequence)
                        .maxCapacity(item.getMaxCapacity())
                        .currentSize(0)
                        .status(ModuleClassStatus.UNSCHEDULED)
                        .build();
                moduleClass.setCreatedAt(now);
                moduleClass.setUpdatedAt(now);
                // TODO(IDENTITY_INTEGRATION): lấy actor từ principal, không nhận userId do client truyền.
                created.add(moduleClass);
            }
        }

        var saved = moduleClassRepository.saveAllAndFlush(created);

        log.info(
                "Mở lớp học phần: semesterId={}, codes={}",
                semesterId,
                saved.stream().map(ModuleClass::getModuleClassCode).toList());
        // TODO(AUDIT_TRAIL): ghi AuditLog có actor và dữ liệu thay đổi khi shared cung cấp contract.
        return saved.stream().map(item -> toResponse(item, subjects, null)).toList();
    }

    @Transactional
    public ModuleClassResponse update(Long id, UpdateModuleClassRequest request) {
        if (request == null
                || request.getMaxCapacity() == null
                || request.getMaxCapacity() < 1
                || request.getMaxCapacity() > ModuleClassPolicy.MAX_CAPACITY)
            throw new BaseException(ErrorCode.VALIDATION_ERROR);

        String reason = requireReason(request.getReason());

        var moduleClass = getForChange(id);

        if (request.getMaxCapacity() < moduleClass.getCurrentSize()) {
            throw new ModuleClassConflictException(
                    ErrorCode.MODULE_CLASS_CAPACITY_BELOW_SIZE,
                    List.of(new FieldErrorDetail(
                            "maxCapacity",
                            "Sĩ số hiện có: " + moduleClass.getCurrentSize() + "; sức chứa mới phải từ "
                                    + moduleClass.getCurrentSize() + " đến " + ModuleClassPolicy.MAX_CAPACITY)));
        }

        var data = lecturerDisplayData(List.of(moduleClass));

        var subjects = indexSubjects(curriculumFacade.findSubjectsByIds(Set.of(moduleClass.getSubjectId())));
        int oldCapacity = moduleClass.getMaxCapacity();

        // Chỉ thay đổi entity sau khi mọi điều kiện và dữ liệu trả về đã được kiểm tra.
        moduleClass.changeCapacity(request.getMaxCapacity());
        moduleClass.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        var saved = moduleClassRepository.saveAndFlush(moduleClass);
        log.info(
                "Cập nhật sức chứa lớp học phần: id={}, code={}, maxCapacity={}->{}, reason={}",
                id,
                saved.getModuleClassCode(),
                oldCapacity,
                saved.getMaxCapacity(),
                reason);
        // TODO(AUDIT_TRAIL): ghi actor và dữ liệu trước/sau vào AuditLog qua contract shared.
        return toResponse(saved, subjects, data);
    }

    @Transactional
    public ModuleClassResponse cancel(Long id, CancelModuleClassRequest request) {

        String reason = requireReason(request == null ? null : request.getReason());
        var moduleClass = getForChange(id);

        if (moduleClass.getCurrentSize() > 0) {
            throw new ModuleClassConflictException(
                    ErrorCode.MODULE_CLASS_HAS_ENROLLMENTS,
                    List.of(new FieldErrorDetail(
                            "currentSize", "Lớp hiện có " + moduleClass.getCurrentSize() + " sinh viên")));
        }

        var usage = enrollmentQuery.summarize(moduleClass.getId(), moduleClass.getModuleClassCode());

        if (usage == null
                || !usage.historyComplete()
                || usage.enrollmentCount() < 0
                || usage.studentCount() < 0
                || usage.studentCount() > usage.enrollmentCount()
                || (usage.enrollmentCount() > 0 && usage.studentCount() == 0)
                || usage.dataSource() == null
                || usage.dataSource().isBlank())
            throw new BaseException(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);

        if (usage.enrollmentCount() > 0) {
            throw new ModuleClassConflictException(
                    ErrorCode.MODULE_CLASS_HAS_ENROLLMENTS,
                    List.of(
                            new FieldErrorDetail(
                                    "enrollmentCount",
                                    "Lớp đã có " + usage.enrollmentCount()
                                            + " bản ghi đăng ký, bao gồm đăng ký đã hủy/ẩn"),
                            new FieldErrorDetail("studentCount", "Số sinh viên liên quan: " + usage.studentCount())));
        }

        var data = lecturerDisplayData(List.of(moduleClass));
        var subjects = indexSubjects(curriculumFacade.findSubjectsByIds(Set.of(moduleClass.getSubjectId())));
        var oldStatus = moduleClass.getStatus();

        // Hủy là trạng thái nghiệp vụ, vẫn giữ bản ghi/mã/STT và không thay đổi Enrollment hay điểm.
        moduleClass.cancel();
        moduleClass.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        // TODO(CLASS_SCHEDULE_INTEGRATION): ngừng hiệu lực lịch liên quan trong cùng transaction.

        var saved = moduleClassRepository.saveAndFlush(moduleClass);

        log.info(
                "Hủy lớp học phần: id={}, code={}, status={}->{}, enrollmentSource={}, reason={}",
                id,
                saved.getModuleClassCode(),
                oldStatus,
                saved.getStatus(),
                usage.dataSource(),
                reason);
        // TODO(AUDIT_TRAIL): ghi actor và lý do hủy vào AuditLog qua contract shared.

        return toResponse(saved, subjects, data);
    }

    private ModuleClass getForChange(Long id) {
        Long semesterId = moduleClassRepository
                .findSemesterIdByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MODULE_CLASS_NOT_FOUND));

        // Cùng thứ tự khóa với mở lớp/cập nhật kỳ. Không tải entity lớp trước khi chờ khóa.
        var semester = semesterRepository
                .findForUpdateByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new BaseException(ErrorCode.SEMESTER_NOT_FOUND));

        requireOpenSemester(semester);

        var moduleClass = moduleClassRepository
                .findForUpdateByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MODULE_CLASS_NOT_FOUND));

        if (moduleClass.getStatus() == ModuleClassStatus.CANCELLED)
            throw new BaseException(ErrorCode.MODULE_CLASS_CANCELLED);

        // TODO(ENROLLMENT_INTEGRATION): đăng ký/hủy đăng ký phải cùng lock hàng lớp rồi kiểm tra trạng thái và
        //  tiến hành cập nhật currentSize, tránh thêm Enrollment trong lúc admin hủy/giảm sức chứa.

        return moduleClass;
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new BaseException(ErrorCode.VALIDATION_ERROR);
        return reason.strip();
    }

    @Transactional(readOnly = true)
    public PageResponse<ModuleClassResponse> search(ModuleClassSearchRequest request) {
        var page = moduleClassRepository.findAll(
                ModuleClassSpecification.search(request),
                PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "id")));

        if (page.isEmpty())
            return PageMapper.toPageResponse(new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements()));

        var subjects = indexSubjects(curriculumFacade.findSubjectsByIds(
                page.stream().map(ModuleClass::getSubjectId).collect(Collectors.toSet())));

        var data = lecturerDisplayData(page.getContent());

        return PageMapper.toPageResponse(page.map(item -> toResponse(item, subjects, data)));
    }

    @Transactional(readOnly = true)
    public ModuleClassResponse findById(Long id) {
        var moduleClass = moduleClassRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MODULE_CLASS_NOT_FOUND));

        var subjects = indexSubjects(curriculumFacade.findSubjectsByIds(Set.of(moduleClass.getSubjectId())));

        return toResponse(moduleClass, subjects, lecturerDisplayData(List.of(moduleClass)));
    }

    private void requireOpenSemester(Semester semester) {
        if (LocalDate.now(clock).isAfter(semester.getEndDate())) {
            throw new BaseException(ErrorCode.SEMESTER_ENDED);
        }

        if (semester.isLocked()) {
            throw new BaseException(ErrorCode.SEMESTER_LOCKED);
        }
    }

    private Set<Long> validateBatch(CreateModuleClassesRequest request) {
        if (request == null
                || request.getSubjects() == null
                || request.getSubjects().isEmpty()) throw new BaseException(ErrorCode.VALIDATION_ERROR);
        Set<Long> subjectIds = new HashSet<>();
        for (var subject : request.getSubjects()) {
            if (subject == null
                    || subject.getSubjectId() == null
                    || subject.getSubjectId() <= 0
                    || subject.getClasses() == null
                    || subject.getClasses().isEmpty()) throw new BaseException(ErrorCode.VALIDATION_ERROR);
            if (!subjectIds.add(subject.getSubjectId())) throw new BaseException(ErrorCode.DUPLICATE_BATCH_SUBJECT);
            for (var item : subject.getClasses()) {
                if (item == null
                        || item.getMaxCapacity() == null
                        || item.getMaxCapacity() < 1
                        || item.getMaxCapacity() > ModuleClassPolicy.MAX_CAPACITY)
                    throw new BaseException(ErrorCode.VALIDATION_ERROR);
            }
        }
        return subjectIds;
    }

    private PlanningSnapshot lecturerDisplayData(Collection<ModuleClass> classes) {
        // Chỉ bổ sung tên cho lớp cũ đã có giảng viên; lớp chưa phân công không cần nguồn People.
        return classes.stream().anyMatch(item -> item.getLecturerId() != null) ? planningDataSource.snapshot() : null;
    }

    private Map<Long, Long> nonCancelledCounts(List<ModuleClass> classes) {
        // Theo quy ước "đã phát sinh": mọi lớp chưa hủy, kể cả lớp đã ẩn, vẫn phải xác nhận.
        return classes.stream()
                .filter(item -> item.getStatus() != ModuleClassStatus.CANCELLED)
                .collect(Collectors.groupingBy(ModuleClass::getSubjectId, Collectors.counting()));
    }

    private Map<Long, SubjectSimpleResponse> indexSubjects(List<SubjectSimpleResponse> subjects) {
        return subjects.stream().collect(Collectors.toMap(SubjectSimpleResponse::getId, subject -> subject));
    }

    private ModuleClassResponse toResponse(
            ModuleClass item, Map<Long, SubjectSimpleResponse> subjects, PlanningSnapshot data) {

        var subject = subjects.get(item.getSubjectId());
        String lecturerName = null;
        if (item.getLecturerId() != null && data != null) {
            var users = data.usersById();
            lecturerName = data.lecturers().stream()
                    .filter(lecturer -> lecturer.id() == item.getLecturerId())
                    .map(lecturer -> users.get(lecturer.userId()))
                    .filter(Objects::nonNull)
                    .map(PlanningSnapshot.User::fullName)
                    .findFirst()
                    .orElse(null);
        }

        return ModuleClassResponse.builder()
                .id(item.getId())
                .semesterId(item.getSemester().getId())
                .semesterCode(item.getSemester().getSemesterCode())
                .subjectId(item.getSubjectId())
                .subjectCode(subject == null ? null : subject.getSubjectCode())
                .subjectName(subject == null ? null : subject.getSubjectName())
                .credits(subject == null ? 0 : subject.getCredits())
                .lecturerId(item.getLecturerId())
                .lecturerName(lecturerName)
                .classSequence(item.getClassSequence())
                .moduleClassCode(item.getModuleClassCode())
                .maxCapacity(item.getMaxCapacity())
                .currentSize(item.getCurrentSize())
                .status(item.getStatus())
                .dataSource(item.getLecturerId() != null && data != null ? data.source() : "DATABASE")
                // TODO(CLASS_SCHEDULE_INTEGRATION): đọc lịch thật khi module xếp lịch được triển khai.
                .schedules(List.of())
                .build();
    }
}
