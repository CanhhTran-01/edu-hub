package com.eduhub.edu_hub.scheduling.application.service;

import static com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.enrollment.application.facade.dto.ModuleClassUsageResponse;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.request.CancelModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateModuleClassesRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.ModuleClassSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.UpdateModuleClassRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassCapacityResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.ModuleClassSimpleResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.OpeningSubjectResponse;
import com.eduhub.edu_hub.scheduling.api.mapper.ModuleClassResponseMapper;
import com.eduhub.edu_hub.scheduling.api.mapper.OpeningSubjectResponseMapper;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassConflictException;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassException;
import com.eduhub.edu_hub.scheduling.application.exception.ModuleClassOpeningException;
import com.eduhub.edu_hub.scheduling.application.exception.SemesterException;
import com.eduhub.edu_hub.scheduling.application.specification.ClassScheduleSpecification;
import com.eduhub.edu_hub.scheduling.application.specification.ModuleClassSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.policy.ModuleClassPolicy;
import com.eduhub.edu_hub.scheduling.domain.policy.OpeningDemandPolicy;
import com.eduhub.edu_hub.scheduling.domain.policy.SemesterPolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.ClassScheduleRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.RoomRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@AllArgsConstructor
public class ModuleClassApplicationService {
    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;
    private final CurriculumFacade.ClassOpening curriculumFacade;
    private final PeopleFacade.Scheduling peopleFacade;
    private final AcademicPlanningSnapshotService academicPlanningSnapshotService;
    private final ModuleClassEnrollmentQueryService moduleClassEnrollmentQueryService;
    private final OpeningDemandPolicy openingDemandPolicy;
    private final EntityManager entityManager;
    private final Clock clock;
    private final ClassScheduleQueryService classScheduleQueryService;
    private final ClassScheduleRepository classScheduleRepository;
    private final RoomRepository roomRepository;
    private final SystemConfigService systemConfigService;

    @Transactional(readOnly = true)
    public ModuleClassCapacityResponse getCapacityConfiguration() {
        var configuration = systemConfigService.getSnapshot();
        return new ModuleClassCapacityResponse(
                configuration.getInteger(MODULE_CLASS_DEFAULT_CAPACITY),
                configuration.getInteger(MODULE_CLASS_MAXIMUM_CAPACITY));
    }

    @Transactional(readOnly = true)
    public List<OpeningSubjectResponse> findSubjectsToOpen(Long semesterId) {

        // lôi kì cần mở lớp lên
        Semester semester = semesterRepository
                .findByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND));

        // check kì vừa lôi lên
        SemesterPolicy.requireClassChangesAllowed(semester.getEndDate(), semester.isLocked(), LocalDate.now(clock));

        // lôi dữ liệu đã mock lên: gồm source, cờ historyComplete, List<Student> students, List<Attempt> attempts
        // source (string): Nguồn gốc của dữ liệu, để biết kết quả tính toán dựa trên dữ liệu nào, tiện log và debug
        // historyComplete: cho biết lịch sử học có đủ ko,vd tưởng SV chưa học môn tiên quyết nên không đưa vào nhu cầu
        // List<Student> students: Danh sách sinh viên thuộc diện lập kế hoạch
        // List<Attempt> attempts: Lịch sử học môn của các SV trên, Mỗi Attempt là một lần 1 SV học một môn
        var planningSnapshot = academicPlanningSnapshotService.loadPlanningSnapshot();

        // lấy các học phần cần mở lớp dùng data vừa xử lý + kì hiện tại + toàn bộ các subject khả dụng trong DB
        var openingSubjects = openingDemandPolicy
                .calculate(
                        semester.getSemesterCode(),
                        curriculumFacade.findPlanningSubjects().stream()
                                .map(planningSubject -> new OpeningDemandPolicy.PlanningSubject(
                                        planningSubject.getCurriculumCode(),
                                        planningSubject.getSubjectId(),
                                        planningSubject.getSubjectCode(),
                                        planningSubject.getSubjectName(),
                                        planningSubject.getCredits(),
                                        planningSubject.getRecommendSemester()))
                                .toList(),
                        planningSnapshot,
                        systemConfigService.getSnapshot().getInteger(MODULE_CLASS_DEFAULT_CAPACITY))
                .stream()
                .map(demand -> OpeningSubjectResponseMapper.toResponse(demand, planningSnapshot))
                .toList();

        // đếm số lớp chưa hủy theo từng môn của kì đang thao tác
        var nonCancelledClassesBySubjectId =
                countNonCancelledClassesBySubjectId(moduleClassRepository.findAllBySemester_IdOrderByIdAsc(semesterId));

        openingSubjects.forEach(planningSubject -> planningSubject.setExistingClassCount(
                nonCancelledClassesBySubjectId.getOrDefault(planningSubject.getSubjectId(), 0L)));

        // trả lại kết quả các môn cần mở lớp học phần
        return openingSubjects;
    }

    @Transactional
    public List<ModuleClassResponse> createModuleClasses(Long semesterId, CreateModuleClassesRequest request) {

        // validate lại request (refactor sau)
        var configuration = systemConfigService.getSnapshot();
        int defaultCapacity = configuration.getInteger(MODULE_CLASS_DEFAULT_CAPACITY);
        int maximumCapacity = configuration.getInteger(MODULE_CLASS_MAXIMUM_CAPACITY);
        Set<Long> subjectIds = validateBatch(request, maximumCapacity);

        // Mọi luồng mở lớp/cập nhật kỳ phải cùng khóa row trong semester_table trước khi kiểm tra và cấp STT.
        Semester semester = semesterRepository
                .findForUpdateByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND));

        // validate nghiệp vụ cho kì học
        SemesterPolicy.requireClassChangesAllowed(semester.getEndDate(), semester.isLocked(), LocalDate.now(clock));

        // gom dữ liệu subject: Map<Long, SubjectSimpleResponse> của lớp học phần
        var subjectsById = indexSubjectsById(curriculumFacade.requireOpenableSubjects(subjectIds));

        // truy vấn cả lớp hủy/soft delete để không bao giờ tái sử dụng STT đã cấp
        List<ModuleClass> historicalClasses = moduleClassRepository.findAllBySemester_IdOrderByIdAsc(semesterId);
        requireAdditionalClassesConfirmed(request, historicalClasses, subjectsById);

        // Cấp mã từ toàn bộ lịch sử; mọi lớp mới vẫn thuộc transaction mở lô hiện tại.
        var createdClasses =
                buildModuleClasses(semesterId, semester, request, historicalClasses, subjectsById, defaultCapacity);

        // ép ghi ngay entity vào DB
        var savedClasses = moduleClassRepository.saveAllAndFlush(createdClasses);
        log.info(
                "Mở lớp học phần: semesterId={}, codes={}",
                semesterId,
                savedClasses.stream().map(ModuleClass::getModuleClassCode).toList());

        // TODO(AUDIT_TRAIL): ghi AuditLog có actor và dữ liệu thay đổi khi shared cung cấp contract.

        return savedClasses.stream()
                .map(savedClass -> ModuleClassResponseMapper.toResponse(savedClass, subjectsById, Map.of()))
                .toList();
    }

    @Transactional
    public ModuleClassResponse updateModuleClassCapacity(Long moduleClassId, UpdateModuleClassRequest request) {

        int maximumCapacity = systemConfigService.getSnapshot().getInteger(MODULE_CLASS_MAXIMUM_CAPACITY);

        // validate lại request
        if (request == null
                || request.getMaxCapacity() == null
                || request.getMaxCapacity() < 1
                || request.getMaxCapacity() > maximumCapacity) {

            throw new ModuleClassException(ErrorCode.VALIDATION_ERROR);
        }

        // normalize + validate
        String reason = ModuleClassPolicy.normalizeChangeReason(request.getReason());

        var moduleClass = lockModuleClassForChange(moduleClassId);

        if (request.getMaxCapacity() < moduleClass.getCurrentSize()) {
            throw new ModuleClassConflictException(
                    ErrorCode.MODULE_CLASS_CAPACITY_BELOW_SIZE,
                    List.of(new FieldErrorDetail(
                            "maxCapacity",
                            "Sĩ số hiện có: " + moduleClass.getCurrentSize() + "; sức chứa mới phải từ "
                                    + moduleClass.getCurrentSize() + " đến " + maximumCapacity)));
        }

        // lấy dữ liệu học phần, giảng viên của lớp học phần, sức chứa
        var lecturerNamesById = loadLecturerNamesById(List.of(moduleClass));
        var subjectsById = indexSubjectsById(curriculumFacade.findSubjectsByIds(Set.of(moduleClass.getSubjectId())));
        int oldCapacity = moduleClass.getMaxCapacity();

        requireScheduledRoomCapacity(moduleClassId, request.getMaxCapacity(), oldCapacity);

        // Chỉ thay đổi entity sau khi mọi điều kiện và dữ liệu trả về đã được kiểm tra.
        moduleClass.changeCapacity(request.getMaxCapacity());
        moduleClass.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        // ghi dữ liệu mới xuống DB
        var savedModuleClass = moduleClassRepository.saveAndFlush(moduleClass);
        log.info(
                "Cập nhật sức chứa lớp học phần: id={}, code={}, maxCapacity={}->{}, reason={}",
                moduleClassId,
                savedModuleClass.getModuleClassCode(),
                oldCapacity,
                savedModuleClass.getMaxCapacity(),
                reason);

        // TODO(AUDIT_TRAIL): ghi actor và dữ liệu trước/sau vào AuditLog qua contract shared.

        return toResponseWithSchedules(savedModuleClass, subjectsById, lecturerNamesById);
    }

    @Transactional
    public ModuleClassResponse cancelModuleClass(Long moduleClassId, CancelModuleClassRequest request) {

        // validate lại reason
        String reason = ModuleClassPolicy.normalizeChangeReason(request == null ? null : request.getReason());

        var moduleClass = lockModuleClassForChange(moduleClassId);

        // lớp học phần đã có sinh viên, không cho hủy
        if (moduleClass.getCurrentSize() > 0) {
            throw new ModuleClassConflictException(
                    ErrorCode.MODULE_CLASS_HAS_ENROLLMENTS,
                    List.of(new FieldErrorDetail(
                            "currentSize", "Lớp hiện có " + moduleClass.getCurrentSize() + " sinh viên")));
        }

        var enrollmentUsage = requireUnusedEnrollmentHistory(moduleClass);

        // lấy dữ liệu học phần, giảng viên của lớp học phần, trạng thái
        var lecturerNamesById = loadLecturerNamesById(List.of(moduleClass));
        var subjectsById = indexSubjectsById(curriculumFacade.findSubjectsByIds(Set.of(moduleClass.getSubjectId())));
        var oldStatus = moduleClass.getStatus();

        // Hủy là trạng thái nghiệp vụ, vẫn giữ bản ghi/mã/STT và không thay đổi Enrollment hay điểm.
        moduleClass.cancel();
        moduleClass.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        // Ngừng hiệu lực lịch cùng transaction hủy lớp; giữ bản ghi lịch sử và không giải phóng mã lớp.
        var schedules = classScheduleRepository.findAll(
                ClassScheduleSpecification.notDeletedAndModuleClassIdIn(List.of(moduleClassId)), Sort.by("id"));
        schedules.forEach(schedule -> {
            schedule.setDeleted(true);
            schedule.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        });
        classScheduleRepository.saveAll(schedules);

        // ghi kết quả sau khi cập nhật xuống DB
        var savedModuleClass = moduleClassRepository.saveAndFlush(moduleClass);
        log.info(
                "Hủy lớp học phần: id={}, code={}, status={}->{}, enrollmentSource={}, reason={}",
                moduleClassId,
                savedModuleClass.getModuleClassCode(),
                oldStatus,
                savedModuleClass.getStatus(),
                enrollmentUsage.dataSource(),
                reason);

        // TODO(AUDIT_TRAIL): ghi actor và lý do hủy vào AuditLog qua contract shared.

        return toResponseWithSchedules(savedModuleClass, subjectsById, lecturerNamesById);
    }

    private ModuleClass lockModuleClassForChange(Long moduleClassId) {

        // có kì tương ứng với lớp học phần này không ?
        Long semesterId = moduleClassRepository
                .findSemesterIdByIdAndDeletedFalse(moduleClassId)
                .orElseThrow(() -> new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND));

        // Cùng thứ tự khóa với mở lớp/cập nhật kỳ. Không tải entity lớp trước khi chờ khóa.
        var semester = semesterRepository
                .findForUpdateByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND));

        // validate cho semester
        SemesterPolicy.requireClassChangesAllowed(semester.getEndDate(), semester.isLocked(), LocalDate.now(clock));

        var moduleClass = moduleClassRepository
                .findForUpdateByIdAndDeletedFalse(moduleClassId)
                .orElseThrow(() -> new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND));

        if (moduleClass.getStatus() == ModuleClassStatus.CANCELLED)
            throw new ModuleClassException(ErrorCode.MODULE_CLASS_CANCELLED);
        if (moduleClass.getStatus() == ModuleClassStatus.COMPLETED)
            throw new ModuleClassException(ErrorCode.TIMETABLE_STATE_CONFLICT);

        // TODO(ENROLLMENT_INTEGRATION): đăng ký/hủy đăng ký phải cùng lock hàng lớp rồi kiểm tra trạng thái và
        //  tiến hành cập nhật currentSize, tránh thêm Enrollment trong lúc admin hủy/giảm sức chứa.

        return moduleClass;
    }

    @Transactional(readOnly = true)
    public PageResponse<ModuleClassSimpleResponse> searchModuleClasses(ModuleClassSearchRequest request) {

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "id"));
        Page<ModuleClass> moduleClassPage =
                moduleClassRepository.findAll(ModuleClassSpecification.search(request), pageable);

        if (moduleClassPage.isEmpty())
            return PageMapper.toPageResponse(
                    new PageImpl<>(List.of(), moduleClassPage.getPageable(), moduleClassPage.getTotalElements()));

        // gom dữ liệu subject: Map<Long, SubjectSimpleResponse>
        var subjectsById = indexSubjectsById(curriculumFacade.findSubjectsByIds(moduleClassPage.stream()
                .map(moduleClass -> moduleClass.getSubjectId())
                .collect(Collectors.toSet())));

        // gom dữ liệu giảng viên: Map<Long, String>
        var lecturerNamesById = loadLecturerNamesById(moduleClassPage.getContent());

        return PageMapper.toPageResponse(moduleClassPage.map(moduleClass ->
                ModuleClassResponseMapper.toSimpleResponse(moduleClass, subjectsById, lecturerNamesById)));
    }

    @Transactional(readOnly = true)
    public ModuleClassResponse findModuleClassById(Long moduleClassId) {

        // lôi lớp học phần lên
        var moduleClass = moduleClassRepository
                .findByIdAndDeletedFalse(moduleClassId)
                .orElseThrow(() -> new ModuleClassException(ErrorCode.MODULE_CLASS_NOT_FOUND));

        // lấy dữ liệu subject: Map<Long, SubjectSimpleResponse> của lớp học phần
        var subjectsById = indexSubjectsById(curriculumFacade.findSubjectsByIds(Set.of(moduleClass.getSubjectId())));

        // lấy dữ liệu giảng viên: Map<Long, String> của lớp học phần
        var lecturerNamesById = loadLecturerNamesById(List.of(moduleClass));

        return toResponseWithSchedules(moduleClass, subjectsById, lecturerNamesById);
    }

    @Transactional(readOnly = true)
    public List<ModuleClassResponse> findSemesterModuleClasses(Long semesterId) {
        var classes = moduleClassRepository.findAllBySemester_IdOrderByIdAsc(semesterId).stream()
                .filter(moduleClass ->
                        !moduleClass.isDeleted() && moduleClass.getStatus() != ModuleClassStatus.CANCELLED)
                .toList();
        if (classes.isEmpty()) return List.of();
        var subjectsById = indexSubjectsById(curriculumFacade.findSubjectsByIds(
                classes.stream().map(moduleClass -> moduleClass.getSubjectId()).collect(Collectors.toSet())));
        var lecturerNamesById = loadLecturerNamesById(classes);
        var schedulesByClassId = classScheduleQueryService.findSchedulesByClassIds(
                classes.stream().map(ModuleClass::getId).toList());
        return classes.stream()
                .map(moduleClass -> ModuleClassResponseMapper.toResponse(
                        moduleClass,
                        subjectsById,
                        lecturerNamesById,
                        schedulesByClassId.getOrDefault(moduleClass.getId(), List.of())))
                .toList();
    }

    private ModuleClassResponse toResponseWithSchedules(
            ModuleClass moduleClass,
            Map<Long, SubjectSimpleResponse> subjectsById,
            Map<Long, String> lecturerNamesById) {
        return ModuleClassResponseMapper.toResponse(
                moduleClass,
                subjectsById,
                lecturerNamesById,
                classScheduleQueryService
                        .findSchedulesByClassIds(List.of(moduleClass.getId()))
                        .getOrDefault(moduleClass.getId(), List.of()));
    }

    private Set<Long> validateBatch(CreateModuleClassesRequest request, int maximumCapacity) {
        if (request == null
                || request.getSubjects() == null
                || request.getSubjects().isEmpty()) throw new ModuleClassException(ErrorCode.VALIDATION_ERROR);

        Set<Long> subjectIds = new HashSet<>();
        for (var subjectRequest : request.getSubjects()) {
            if (subjectRequest == null
                    || subjectRequest.getSubjectId() == null
                    || subjectRequest.getSubjectId() <= 0
                    || subjectRequest.getClasses() == null
                    || subjectRequest.getClasses().isEmpty())
                throw new ModuleClassException(ErrorCode.VALIDATION_ERROR);

            if (!subjectIds.add(subjectRequest.getSubjectId()))
                throw new ModuleClassException(ErrorCode.DUPLICATE_BATCH_SUBJECT);

            for (var classRequest : subjectRequest.getClasses()) {
                if (classRequest == null
                        || !classRequest.isMaxCapacitySpecifiedCorrectly()
                        || (classRequest.getMaxCapacity() != null
                                && (classRequest.getMaxCapacity() < 1
                                        || classRequest.getMaxCapacity() > maximumCapacity)))
                    throw new ModuleClassException(ErrorCode.VALIDATION_ERROR);
            }
        }
        return subjectIds;
    }

    private Map<Long, String> loadLecturerNamesById(Collection<ModuleClass> moduleClasses) {

        var lecturerIds = moduleClasses.stream()
                .map(ModuleClass::getLecturerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (lecturerIds.isEmpty()) return Map.of();

        return peopleFacade.findLecturersByIds(lecturerIds).stream()
                .collect(Collectors.toMap(LecturerResponse::getId, LecturerResponse::getFullName));
    }

    private Map<Long, Long> countNonCancelledClassesBySubjectId(List<ModuleClass> moduleClasses) {
        // Theo quy ước "đã phát sinh": mọi lớp chưa hủy, kể cả lớp đã ẩn, vẫn phải xác nhận.
        return moduleClasses.stream()
                .filter(moduleClass -> moduleClass.getStatus() != ModuleClassStatus.CANCELLED)
                .collect(Collectors.groupingBy(moduleClass -> moduleClass.getSubjectId(), Collectors.counting()));
    }

    private Map<Long, SubjectSimpleResponse> indexSubjectsById(List<SubjectSimpleResponse> subjectResponses) {
        return subjectResponses.stream()
                .collect(Collectors.toMap(SubjectSimpleResponse::getId, subjectResponse -> subjectResponse));
    }

    private void requireAdditionalClassesConfirmed(
            CreateModuleClassesRequest request,
            List<ModuleClass> historicalClasses,
            Map<Long, SubjectSimpleResponse> subjectsById) {
        var nonCancelledClassesBySubjectId =
                countNonCancelledClassesBySubjectId(historicalClasses); // đêm số lớp chưa hủy theo môn, lưu vào map

        var openingWarnings = new ArrayList<FieldErrorDetail>();
        for (int i = 0; i < request.getSubjects().size(); i++) {
            var subjectRequest = request.getSubjects().get(i);
            long count = nonCancelledClassesBySubjectId.getOrDefault(subjectRequest.getSubjectId(), 0L);
            if (count > 0 && !subjectRequest.isConfirmAdditionalClasses()) {
                openingWarnings.add(new FieldErrorDetail(
                        "subjects[" + i + "].subjectId",
                        subjectsById.get(subjectRequest.getSubjectId()).getSubjectCode() + " đã có " + count
                                + " lớp chưa hủy trong kỳ; gửi confirmAdditionalClasses=true để mở thêm"));
            }
        }

        // gửi lại cảnh báo cho clients bấm xác nhận khi tạo thêm lớp học phần cho cùng 1 môn
        if (!openingWarnings.isEmpty())
            throw new ModuleClassOpeningException(ErrorCode.MODULE_CLASS_CONFIRMATION_REQUIRED, openingWarnings);
    }

    private List<ModuleClass> buildModuleClasses(
            Long semesterId,
            Semester semester,
            CreateModuleClassesRequest request,
            List<ModuleClass> historicalClasses,
            Map<Long, SubjectSimpleResponse> subjectsById,
            int defaultCapacity) {
        // Tìm số thứ tự lớn nhất hiện có của mỗi môn
        Map<Long, Integer> lastSequencesBySubjectId = new HashMap<>();
        historicalClasses.forEach(historicalClass -> lastSequencesBySubjectId.merge(
                historicalClass.getSubjectId(), historicalClass.getClassSequence(), Math::max));

        var createdClasses = new ArrayList<ModuleClass>();
        LocalDateTime createdAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (var subjectRequest : request.getSubjects()) {
            // Facade đã kiểm tra và khóa môn; lớp chỉ giữ ID đã xác nhận, không tạo proxy liên module.
            for (var classRequest : subjectRequest.getClasses()) {

                // môn chưa có key thì đặt giá trị 1; đã có thì cộng 1 vào giá trị cũ. merge trả về giá trị mới.
                int sequence = lastSequencesBySubjectId.merge(subjectRequest.getSubjectId(), 1, Integer::sum);

                String moduleClassCode = ModuleClassPolicy.generateModuleClassCode(
                        semesterId,
                        subjectsById.get(subjectRequest.getSubjectId()).getSubjectCode(),
                        sequence);

                // tạo mới entity
                var moduleClass = ModuleClass.builder()
                        .semester(semester)
                        .subjectId(subjectRequest.getSubjectId())
                        .moduleClassCode(moduleClassCode)
                        .classSequence(sequence)
                        .maxCapacity(
                                classRequest.getMaxCapacity() == null ? defaultCapacity : classRequest.getMaxCapacity())
                        .currentSize(0)
                        .status(ModuleClassStatus.UNSCHEDULED) // trạng thái sau khi tạo lớp
                        .build();
                moduleClass.setCreatedAt(createdAt);
                moduleClass.setUpdatedAt(createdAt);

                // TODO(IDENTITY_INTEGRATION): lấy actor từ principal, không nhận userId do client truyền.

                createdClasses.add(moduleClass);
            }
        }

        return createdClasses;
    }

    private void requireScheduledRoomCapacity(Long moduleClassId, int maxCapacity, int oldCapacity) {
        // Tăng sức chứa phải đồng bộ với luồng giảm capacity phòng: khóa phòng trước khi đối chiếu.
        if (maxCapacity > oldCapacity) {
            var schedules = classScheduleRepository.findAll(
                    ClassScheduleSpecification.notDeletedAndModuleClassIdIn(List.of(moduleClassId)), Sort.by("id"));
            for (Long roomId : schedules.stream()
                    .map(schedule -> schedule.getRoom().getId())
                    .distinct()
                    .sorted()
                    .toList()) {
                var room = roomRepository
                        .findForUpdateByIdAndDeletedFalse(roomId)
                        .orElseThrow(() -> new ModuleClassException(ErrorCode.ROOM_NOT_FOUND));
                // Lịch đã tải Room vào persistence context trước lúc chờ khóa; phải đọc lại trạng thái mới nhất.
                entityManager.refresh(room, LockModeType.PESSIMISTIC_WRITE);
                if (room.getCapacity() < maxCapacity)
                    throw new ModuleClassConflictException(
                            ErrorCode.ROOM_CAPACITY_BELOW_CLASS,
                            List.of(new FieldErrorDetail(
                                    "maxCapacity",
                                    "Phòng " + room.getRoomCode() + " chỉ có sức chứa " + room.getCapacity())));
            }
        }
    }

    private ModuleClassUsageResponse requireUnusedEnrollmentHistory(ModuleClass moduleClass) {
        // kiểm tra dữ liệu đăng ký
        var enrollmentUsage = moduleClassEnrollmentQueryService.summarizeModuleClassEnrollments(moduleClass.getId());

        // dữ liệu bị khuyết
        if (enrollmentUsage == null
                || !enrollmentUsage.historyComplete()
                || enrollmentUsage.enrollmentCount() < 0
                || enrollmentUsage.studentCount() < 0
                || enrollmentUsage.studentCount() > enrollmentUsage.enrollmentCount()
                || (enrollmentUsage.enrollmentCount() > 0 && enrollmentUsage.studentCount() == 0)
                || enrollmentUsage.dataSource() == null
                || enrollmentUsage.dataSource().isBlank())
            throw new ModuleClassException(ErrorCode.MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE);

        // đã có bản ghi đăng ký
        if (enrollmentUsage.enrollmentCount() > 0) {
            throw new ModuleClassConflictException(
                    ErrorCode.MODULE_CLASS_HAS_ENROLLMENTS,
                    List.of(
                            new FieldErrorDetail(
                                    "enrollmentCount",
                                    "Lớp đã có " + enrollmentUsage.enrollmentCount()
                                            + " bản ghi đăng ký, bao gồm đăng ký đã hủy/ẩn"),
                            new FieldErrorDetail(
                                    "studentCount", "Số sinh viên liên quan: " + enrollmentUsage.studentCount())));
        }

        return enrollmentUsage;
    }
}
