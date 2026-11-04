package com.eduhub.edu_hub.scheduling.application;

import com.eduhub.edu_hub.scheduling.api.dto.CreateSemesterRequest;
import com.eduhub.edu_hub.scheduling.api.dto.SemesterResponse;
import com.eduhub.edu_hub.scheduling.api.dto.SemesterSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.UpdateSemesterRequest;
import com.eduhub.edu_hub.scheduling.application.exception.SemesterConflictException;
import com.eduhub.edu_hub.scheduling.application.specification.SemesterSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.enums.SemesterStatus;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class SemesterApplicationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern SEMESTER_NAME =
            Pattern.compile("^Học kì ([1-9][0-9]*) năm học ([0-9]{4})-([0-9]{4})$");

    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;
    private final Clock clock;

    @Autowired
    public SemesterApplicationService(
            SemesterRepository semesterRepository, ModuleClassRepository moduleClassRepository) {
        this(semesterRepository, moduleClassRepository, Clock.system(BUSINESS_ZONE));
    }

    SemesterApplicationService(
            SemesterRepository semesterRepository, ModuleClassRepository moduleClassRepository, Clock clock) {
        this.semesterRepository = semesterRepository;
        this.moduleClassRepository = moduleClassRepository;
        this.clock = clock.withZone(BUSINESS_ZONE);
    }

    @Transactional
    public SemesterResponse createSemester(CreateSemesterRequest request) {

        // chuẩn hóa lại tên kì học
        String name = normalizeName(request.getSemesterName());

        // vaidate nghiệp vụ
        validateDates(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationStart(),
                request.getRegistrationEnd());

        // check trùng tên
        if (semesterRepository.existsBySemesterName(name)) {
            throw new BaseException(ErrorCode.DUPLICATE_SEMESTER_NAME);
        }

        // check khoảng thời gian chồng lấn
        checkOverlaps(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationStart(),
                request.getRegistrationEnd(),
                null);

        // tạo entity
        Semester semester = Semester.builder()
                .semesterName(name)
                .semesterCode(toCode(name))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .registrationStart(request.getRegistrationStart())
                .registrationEnd(request.getRegistrationEnd())
                .build();

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        semester.setCreatedAt(now);
        semester.setUpdatedAt(now);

        // ghi xuống database (ép ghi ngay)
        Semester saved = semesterRepository.saveAndFlush(semester);
        logChange("Tạo", saved);

        return toResponse(saved, LocalDate.now(clock));
    }

    @Transactional
    public SemesterResponse updateSemester(Long id, UpdateSemesterRequest request) {

        Semester semester = semesterRepository
                .findForUpdateByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.SEMESTER_NOT_FOUND));

        // Kì này end rồi thì không cho cập nhật ?
        LocalDate today = LocalDate.now(clock);
        if (today.isAfter(semester.getEndDate())) {
            throw new BaseException(ErrorCode.SEMESTER_ENDED);
        }

        // chuẩn hóa tên
        String name = normalizeName(request.getSemesterName());

        // validate nghiệp vụ cho ngày
        validateDates(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationStart(),
                request.getRegistrationEnd());

        // check trùng tên
        if (semesterRepository.existsBySemesterNameAndIdNot(name, id)) {
            throw new BaseException(ErrorCode.DUPLICATE_SEMESTER_NAME);
        }

        // check khoảng thời gian chồng lấn
        checkOverlaps(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationStart(),
                request.getRegistrationEnd(),
                id);

        // TODO: cần làm rõ tại đây, cho cập nhật khoảng date hay không ? chắc là không
        // check ngày thay đổi
        boolean datesChanged = !semester.getStartDate().equals(request.getStartDate())
                || !semester.getEndDate().equals(request.getEndDate());

        // TODO: đổi ngày đăng kí thì sao ? - ở use-case mở đăng ký

        // date chưa bị thay đổi, phải được xác nhận từ client, kì chưa có lớp hoặc có nhưng đã hủy thì mới update được
        if (datesChanged
                && !request.isConfirmDateChange()
                && moduleClassRepository.existsBySemester_IdAndStatusNot(id, ModuleClassStatus.CANCELLED)) {
            throw new BaseException(ErrorCode.SEMESTER_DATE_CHANGE_CONFIRMATION_REQUIRED);
        }

        // TODO(CLASS_SCHEDULE_INTEGRATION): chekc lịch đã xếp vẫn nằm trong khoảng ngày mới (nếu cho update date).

        // cập nhật field
        semester.setSemesterName(name);
        semester.setSemesterCode(toCode(name));
        semester.setStartDate(request.getStartDate());
        semester.setEndDate(request.getEndDate());
        semester.setRegistrationStart(request.getRegistrationStart());
        semester.setRegistrationEnd(request.getRegistrationEnd());
        semester.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        // ghi xuống database
        Semester saved = semesterRepository.saveAndFlush(semester);
        logChange("Cập nhật", saved);

        return toResponse(saved, today);
    }

    @Transactional(readOnly = true)
    public SemesterResponse getSemesterById(Long id) {

        Semester semester = semesterRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.SEMESTER_NOT_FOUND));

        LocalDate today = LocalDate.now(clock);
        return toResponse(semester, today);
    }

    @Transactional(readOnly = true)
    public PageResponse<SemesterResponse> searchSemesters(SemesterSearchRequest request) {

        LocalDate today = LocalDate.now(clock);

        var pageable =
                PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "startDate", "id"));

        var semesters = semesterRepository.findAll(SemesterSpecification.search(request, today), pageable);

        return PageMapper.toPageResponse(semesters.map(semester -> toResponse(semester, today)));
    }

    private String normalizeName(String rawName) {
        if (rawName == null) {
            throw new BaseException(ErrorCode.INVALID_SEMESTER_NAME);
        }

        String name = rawName.trim();
        Matcher matcher = SEMESTER_NAME.matcher(name);
        if (!matcher.matches()) {
            throw new BaseException(ErrorCode.INVALID_SEMESTER_NAME);
        }

        int firstYear = Integer.parseInt(matcher.group(2));
        int secondYear = Integer.parseInt(matcher.group(3));

        if (firstYear < 1 || secondYear != firstYear + 1) {
            throw new BaseException(ErrorCode.INVALID_SEMESTER_NAME);
        }

        return name;
    }

    private String toCode(String name) {
        return SEMESTER_NAME.matcher(name).replaceFirst("HK_$1_$2_$3");
    }

    private void validateDates(
            LocalDate semesterStart, LocalDate semesterEnd, LocalDate registrationStart, LocalDate registrationEnd) {
        if (semesterStart == null || semesterEnd == null || registrationStart == null || registrationEnd == null) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }

        // kiểm tra khoảng thời gian hợp lệ
        // registrationStart < registrationEnd < semesterStart < semesterEnd
        if (!semesterStart.isBefore(semesterEnd)) {
            throw new BaseException(ErrorCode.INVALID_SEMESTER_DATES);
        }
        if (!registrationStart.isBefore(registrationEnd) || !registrationEnd.isBefore(semesterStart)) {
            throw new BaseException(ErrorCode.INVALID_REGISTRATION_PERIOD);
        }
    }

    private void checkOverlaps(
            LocalDate start, LocalDate end, LocalDate registrationStart, LocalDate registrationEnd, Long excludedId) {
        List<Semester> studyConflicts = semesterRepository.findAll(
                SemesterSpecification.overlappingStudyPeriod(start, end, excludedId), Sort.by("id"));
        if (!studyConflicts.isEmpty()) {
            throw new SemesterConflictException(
                    ErrorCode.SEMESTER_DATE_OVERLAP,
                    studyConflicts.stream()
                            .map(semester -> new FieldErrorDetail(
                                    "studyPeriod",
                                    semester.getSemesterName() + " (" + semester.getStartDate() + " - "
                                            + semester.getEndDate() + ")"))
                            .toList());
        }
        List<Semester> registrationConflicts = semesterRepository.findAll(
                SemesterSpecification.overlappingRegistrationPeriod(registrationStart, registrationEnd, excludedId),
                Sort.by("id"));
        if (!registrationConflicts.isEmpty()) {
            throw new SemesterConflictException(
                    ErrorCode.SEMESTER_REGISTRATION_OVERLAP,
                    registrationConflicts.stream()
                            .map(semester -> new FieldErrorDetail(
                                    "registrationPeriod",
                                    semester.getSemesterName() + " (" + semester.getRegistrationStart() + " - "
                                            + semester.getRegistrationEnd() + ")"))
                            .toList());
        }
    }

    private SemesterResponse toResponse(Semester semester, LocalDate today) {

        // lấy giờ trong clock, check và cập nhật động trạng thái kì
        SemesterStatus status = today.isBefore(semester.getStartDate())
                ? SemesterStatus.UPCOMING
                : today.isAfter(semester.getEndDate()) ? SemesterStatus.ENDED : SemesterStatus.ONGOING;

        // TODO: isLocked và lockedGrade cần phân định rõ ràng, có thực sự cần isLocked không ?

        return SemesterResponse.builder()
                .id(semester.getId())
                .semesterCode(semester.getSemesterCode())
                .semesterName(semester.getSemesterName())
                .startDate(semester.getStartDate())
                .endDate(semester.getEndDate())
                .registrationStart(semester.getRegistrationStart())
                .registrationEnd(semester.getRegistrationEnd())
                .status(status)
                .isLocked(semester.isLocked())
                .lockedGrade(semester.isLockedGrade())
                .build();
    }

    private void logChange(String action, Semester semester) {
        log.info(
                "{} kỳ học: id={}, code={}, startDate={}, endDate={}, registrationStart={}, registrationEnd={}",
                action,
                semester.getId(),
                semester.getSemesterCode(),
                semester.getStartDate(),
                semester.getEndDate(),
                semester.getRegistrationStart(),
                semester.getRegistrationEnd());
    }
}
