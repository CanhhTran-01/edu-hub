package com.eduhub.edu_hub.scheduling.application;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.scheduling.api.dto.request.CancelModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateModuleClassesRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.UpdateModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.OpeningSubjectResponse;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassConflictException;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassOpeningException;
import com.eduhub.edu_hub.scheduling.application.port.AcademicPlanningDataSource;
import com.eduhub.edu_hub.scheduling.application.port.ModuleClassEnrollmentQuery;
import com.eduhub.edu_hub.scheduling.application.specification.ModuleClassSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.policy.ModuleClassPolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ModuleClassApplicationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;
    private final CurriculumFacade.ClassOpening curriculumFacade;
    private final PeopleFacade.Scheduling peopleFacade;
    private final AcademicPlanningDataSource planningDataSource;
    private final ModuleClassEnrollmentQuery enrollmentQuery;
    private final OpeningDemandCalculator demandCalculator;
    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired
    public ModuleClassApplicationService(
            SemesterRepository semesterRepository,
            ModuleClassRepository moduleClassRepository,
            CurriculumFacade.ClassOpening curriculumFacade,
            PeopleFacade.Scheduling peopleFacade,
            AcademicPlanningDataSource planningDataSource,
            ModuleClassEnrollmentQuery enrollmentQuery,
            OpeningDemandCalculator demandCalculator,
            EntityManager entityManager) {
        this(
                semesterRepository,
                moduleClassRepository,
                curriculumFacade,
                peopleFacade,
                planningDataSource,
                enrollmentQuery,
                demandCalculator,
                entityManager,
                Clock.system(BUSINESS_ZONE));
    }

    ModuleClassApplicationService(
            SemesterRepository semesterRepository,
            ModuleClassRepository moduleClassRepository,
            CurriculumFacade.ClassOpening curriculumFacade,
            PeopleFacade.Scheduling peopleFacade,
            AcademicPlanningDataSource planningDataSource,
            ModuleClassEnrollmentQuery enrollmentQuery,
            OpeningDemandCalculator demandCalculator,
            EntityManager entityManager,
            Clock clock) {
        this.semesterRepository = semesterRepository;
        this.moduleClassRepository = moduleClassRepository;
        this.curriculumFacade = curriculumFacade;
        this.peopleFacade = peopleFacade;
        this.planningDataSource = planningDataSource;
        this.enrollmentQuery = enrollmentQuery;
        this.demandCalculator = demandCalculator;
        this.entityManager = entityManager;
        this.clock = clock.withZone(BUSINESS_ZONE);
    }

    @Transactional(readOnly = true)
    public List<OpeningSubjectResponse> findOpeningSubjects(Long semesterId) {

        // lôi kì cần mở lớp lên
        Semester semester = semesterRepository
                .findByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new BaseException(ErrorCode.SEMESTER_NOT_FOUND));

        // check kì vừa lôi lên
        requireOpenSemester(semester);

        // lôi dữ liệu đã mock lên: gồm source, cờ historyComplete, List<Student> students, List<Attempt> attempts
        // source (string): Nguồn gốc của dữ liệu, để biết kết quả tính toán dựa trên dữ liệu nào, tiện log và debug
        // historyComplete: cho biết lịch sử học có đủ ko,vd tưởng SV chưa học môn tiên quyết nên không đưa vào nhu cầu
        // List<Student> students: Danh sách sinh viên thuộc diện lập kế hoạch
        // List<Attempt> attempts: Lịch sử học môn của các SV trên, Mỗi Attempt là một lần 1 SV học một môn
        var data = planningDataSource.snapshot();

        // lấy các học phần cần mở lớp dùng data vừa xử lý + kì hiện tại + toàn bộ các subject khả dụng trong DB
        var subjects =
                demandCalculator.calculate(semester.getSemesterCode(), curriculumFacade.findPlanningSubjects(), data);

        // đếm số lớp chưa hủy theo từng môn của kì đang thao tác
        var existing = nonCancelledCounts(moduleClassRepository.findAllBySemester_IdOrderByIdAsc(semesterId));

        subjects.forEach(subject -> subject.setExistingClassCount(existing.getOrDefault(subject.getSubjectId(), 0L)));

        // trả lại kết quả các môn cần mở lớp học phần
        return subjects;
    }

    @Transactional
    public List<ModuleClassResponse> createBatch(Long semesterId, CreateModuleClassesRequest request) {

        // validate lại request (refactor sau)
        Set<Long> subjectIds = validateBatch(request);

        // Mọi luồng mở lớp/cập nhật kỳ phải cùng khóa row trong semester_table trước khi kiểm tra và cấp STT.
        Semester semester = semesterRepository
                .findForUpdateByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new BaseException(ErrorCode.SEMESTER_NOT_FOUND));

        // validate nghiệp vụ cho kì học
        requireOpenSemester(semester);

        // gom dữ liệu subject: Map<Long, SubjectSimpleResponse> của lớp học phần
        var subjects = indexSubjects(curriculumFacade.requireOpenableSubjects(subjectIds));

        // truy vấn cả lớp hủy/soft delete để không bao giờ tái sử dụng STT đã cấp
        List<ModuleClass> history = moduleClassRepository.findAllBySemester_IdOrderByIdAsc(semesterId);
        var existing = nonCancelledCounts(history); // đêm số lớp chưa hủy theo môn, lưu vào map

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

        // gửi lại cảnh báo cho clients bấm xác nhận khi tạo thêm lớp học phần cho cùng 1 môn
        if (!warnings.isEmpty())
            throw new ModuleClassOpeningException(ErrorCode.MODULE_CLASS_CONFIRMATION_REQUIRED, warnings);

        // Tìm số thứ tự lớn nhất hiện có của mỗi môn
        Map<Long, Integer> sequences = new HashMap<>();
        history.forEach(item -> sequences.merge(item.getSubject().getId(), item.getClassSequence(), Math::max));

        var created = new ArrayList<ModuleClass>();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (var subject : request.getSubjects()) {
            // Facade đã kiểm tra và khóa môn; reference chỉ dùng để gán quan hệ, không thay thế validation.
            var subjectReference = entityManager.getReference(Subject.class, subject.getSubjectId());
            for (var item : subject.getClasses()) {

                // môn chưa có key thì đặt giá trị 1; đã có thì cộng 1 vào giá trị cũ. merge trả về giá trị mới.
                int sequence = sequences.merge(subject.getSubjectId(), 1, Integer::sum);

                // Mã lớp dạng MC_<semesterId>_<mã môn>_<seq 3 chữ số>
                String code = String.format(
                        Locale.ROOT,
                        "MC_%d_%s_%03d",
                        semesterId,
                        subjects.get(subject.getSubjectId()).getSubjectCode(),
                        sequence);

                // tạo mới entity
                var moduleClass = ModuleClass.builder()
                        .semester(semester)
                        .subject(subjectReference)
                        .moduleClassCode(code)
                        .classSequence(sequence)
                        .maxCapacity(item.getMaxCapacity())
                        .currentSize(0)
                        .status(ModuleClassStatus.UNSCHEDULED) // trạng thái sau khi tạo lớp
                        .build();
                moduleClass.setCreatedAt(now);
                moduleClass.setUpdatedAt(now);

                // TODO(IDENTITY_INTEGRATION): lấy actor từ principal, không nhận userId do client truyền.

                created.add(moduleClass);
            }
        }

        // ép ghi ngay entity vào DB
        var saved = moduleClassRepository.saveAllAndFlush(created);
        log.info(
                "Mở lớp học phần: semesterId={}, codes={}",
                semesterId,
                saved.stream().map(ModuleClass::getModuleClassCode).toList());

        // TODO(AUDIT_TRAIL): ghi AuditLog có actor và dữ liệu thay đổi khi shared cung cấp contract.

        return saved.stream().map(item -> toResponse(item, subjects, Map.of())).toList();
    }

    @Transactional
    public ModuleClassResponse update(Long id, UpdateModuleClassRequest request) {

        // validate lại request
        if (request == null
                || request.getMaxCapacity() == null
                || request.getMaxCapacity() < 1
                || request.getMaxCapacity() > ModuleClassPolicy.MAX_CAPACITY) {

            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }

        // normalize + validate
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

        // lấy dữ liệu học phần, giảng viên của lớp học phần, sức chứa
        var data = lecturerDisplayData(List.of(moduleClass));
        var subjects = indexSubjects(curriculumFacade.findSubjectsByIds(
                Set.of(moduleClass.getSubject().getId())));
        int oldCapacity = moduleClass.getMaxCapacity();

        // Chỉ thay đổi entity sau khi mọi điều kiện và dữ liệu trả về đã được kiểm tra.
        moduleClass.changeCapacity(request.getMaxCapacity());
        moduleClass.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        // ghi dữ liệu mới xuống DB
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

        // validate lại reason
        String reason = requireReason(request == null ? null : request.getReason());

        var moduleClass = getForChange(id);

        // lớp học phần đã có sinh viên, không cho hủy
        if (moduleClass.getCurrentSize() > 0) {
            throw new ModuleClassConflictException(
                    ErrorCode.MODULE_CLASS_HAS_ENROLLMENTS,
                    List.of(new FieldErrorDetail(
                            "currentSize", "Lớp hiện có " + moduleClass.getCurrentSize() + " sinh viên")));
        }

        // kiểm tra dữ liệu đăng ký
        var usage = enrollmentQuery.summarize(moduleClass.getId(), moduleClass.getModuleClassCode());

        // dữ liệu bị khuyết
        if (usage == null
                || !usage.historyComplete()
                || usage.enrollmentCount() < 0
                || usage.studentCount() < 0
                || usage.studentCount() > usage.enrollmentCount()
                || (usage.enrollmentCount() > 0 && usage.studentCount() == 0)
                || usage.dataSource() == null
                || usage.dataSource().isBlank())
            throw new BaseException(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);

        // đã có bản ghi đăng ký
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

        // lấy dữ liệu học phần, giảng viên của lớp học phần, trạng thái
        var data = lecturerDisplayData(List.of(moduleClass));
        var subjects = indexSubjects(curriculumFacade.findSubjectsByIds(
                Set.of(moduleClass.getSubject().getId())));
        var oldStatus = moduleClass.getStatus();

        // Hủy là trạng thái nghiệp vụ, vẫn giữ bản ghi/mã/STT và không thay đổi Enrollment hay điểm.
        moduleClass.cancel();
        moduleClass.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        // TODO(CLASS_SCHEDULE_INTEGRATION): ngừng hiệu lực lịch liên quan trong cùng transaction.

        // ghi kết quả sau khi cập nhật xuống DB
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

        // có kì tương ứng với lớp học phần này không ?
        Long semesterId = moduleClassRepository
                .findSemesterIdByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MODULE_CLASS_NOT_FOUND));

        // Cùng thứ tự khóa với mở lớp/cập nhật kỳ. Không tải entity lớp trước khi chờ khóa.
        var semester = semesterRepository
                .findForUpdateByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new BaseException(ErrorCode.SEMESTER_NOT_FOUND));

        // validate cho semester
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

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "id"));
        Page<ModuleClass> page = moduleClassRepository.findAll(ModuleClassSpecification.search(request), pageable);

        if (page.isEmpty())
            return PageMapper.toPageResponse(new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements()));

        // gom dữ liệu subject: Map<Long, SubjectSimpleResponse>
        var subjects = indexSubjects(curriculumFacade.findSubjectsByIds(
                page.stream().map(item -> item.getSubject().getId()).collect(Collectors.toSet())));

        // gom dữ liệu giảng viên: Map<Long, String>
        var lecturerData = lecturerDisplayData(page.getContent());

        return PageMapper.toPageResponse(page.map(item -> toResponse(item, subjects, lecturerData)));
    }

    @Transactional(readOnly = true)
    public ModuleClassResponse findById(Long id) {

        // lôi lớp học phần lên
        var moduleClass = moduleClassRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MODULE_CLASS_NOT_FOUND));

        // lấy dữ liệu subject: Map<Long, SubjectSimpleResponse> của lớp học phần
        var subjects = indexSubjects(curriculumFacade.findSubjectsByIds(
                Set.of(moduleClass.getSubject().getId())));

        // lấy dữ liệu giảng viên: Map<Long, String> của lớp học phần
        var lecturerData = lecturerDisplayData(List.of(moduleClass));

        return toResponse(moduleClass, subjects, lecturerData);
    }

    private void requireOpenSemester(Semester semester) {

        // kỳ học đã kết thúc
        if (LocalDate.now(clock).isAfter(semester.getEndDate())) {
            throw new BaseException(ErrorCode.SEMESTER_ENDED);
        }

        // kì học đã khóa
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

    private Map<Long, String> lecturerDisplayData(Collection<ModuleClass> classes) {

        var ids = classes.stream()
                .map(ModuleClass::getLecturer)
                .filter(Objects::nonNull)
                .map(Lecturer::getId)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) return Map.of();

        return peopleFacade.findLecturersByIds(ids).stream()
                .collect(Collectors.toMap(LecturerResponse::getId, LecturerResponse::getFullName));
    }

    private Map<Long, Long> nonCancelledCounts(List<ModuleClass> classes) {
        // Theo quy ước "đã phát sinh": mọi lớp chưa hủy, kể cả lớp đã ẩn, vẫn phải xác nhận.
        return classes.stream()
                .filter(item -> item.getStatus() != ModuleClassStatus.CANCELLED)
                .collect(Collectors.groupingBy(item -> item.getSubject().getId(), Collectors.counting()));
    }

    private Map<Long, SubjectSimpleResponse> indexSubjects(List<SubjectSimpleResponse> subjects) {
        return subjects.stream().collect(Collectors.toMap(SubjectSimpleResponse::getId, subject -> subject));
    }

    private ModuleClassResponse toResponse(
            ModuleClass item, Map<Long, SubjectSimpleResponse> subjects, Map<Long, String> lecturerNames) {

        var subject = subjects.get(item.getSubject().getId());
        Long lecturerId = item.getLecturer() == null ? null : item.getLecturer().getId();
        String lecturerName = lecturerId == null ? null : lecturerNames.get(lecturerId);

        return ModuleClassResponse.builder()
                .id(item.getId())
                .semesterId(item.getSemester().getId())
                .semesterCode(item.getSemester().getSemesterCode())
                .subjectId(item.getSubject().getId())
                .subjectCode(subject == null ? null : subject.getSubjectCode())
                .subjectName(subject == null ? null : subject.getSubjectName())
                .credits(subject == null ? 0 : subject.getCredits())
                .lecturerId(lecturerId)
                .lecturerName(lecturerName)
                .classSequence(item.getClassSequence())
                .moduleClassCode(item.getModuleClassCode())
                .maxCapacity(item.getMaxCapacity())
                .currentSize(item.getCurrentSize())
                .status(item.getStatus())
                .dataSource("DATABASE")
                // TODO(CLASS_SCHEDULE_INTEGRATION): đọc lịch thật khi module xếp lịch được triển khai.
                .schedules(List.of())
                .build();
    }
}
