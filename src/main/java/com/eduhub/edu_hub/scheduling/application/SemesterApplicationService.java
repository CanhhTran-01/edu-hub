package com.eduhub.edu_hub.scheduling.application;

import com.eduhub.edu_hub.scheduling.api.dto.CreateSemesterRequest;
import com.eduhub.edu_hub.scheduling.api.dto.SemesterResponse;
import com.eduhub.edu_hub.scheduling.api.dto.SemesterSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.UpdateSemesterRequest;
import com.eduhub.edu_hub.scheduling.application.exception.SemesterConflictException;
import com.eduhub.edu_hub.scheduling.application.specification.SemesterSpecification;
import com.eduhub.edu_hub.scheduling.domain.entity.Semester;
import com.eduhub.edu_hub.scheduling.domain.enums.SemesterStatus;
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
    private final Clock clock;

    @Autowired
    public SemesterApplicationService(SemesterRepository semesterRepository) {
        this(semesterRepository, Clock.system(BUSINESS_ZONE));
    }

    SemesterApplicationService(SemesterRepository semesterRepository, Clock clock) {
        this.semesterRepository = semesterRepository;
        this.clock = clock.withZone(BUSINESS_ZONE);
    }

    @Transactional
    public SemesterResponse createSemester(CreateSemesterRequest request) {
        String name = normalizeName(request.getSemesterName());
        validateDates(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationStart(),
                request.getRegistrationEnd());

        if (semesterRepository.existsBySemesterName(name)) {
            throw new BaseException(ErrorCode.DUPLICATE_SEMESTER_NAME);
        }

        checkOverlaps(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationStart(),
                request.getRegistrationEnd(),
                null);

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

        Semester saved = semesterRepository.saveAndFlush(semester);

        logChange("Tạo", saved);
        return toResponse(saved, LocalDate.now(clock));
    }

    @Transactional
    public SemesterResponse updateSemester(Long id, UpdateSemesterRequest request) {
        Semester semester = getForUpdate(id);
        LocalDate today = LocalDate.now(clock);
        checkNotEnded(semester, today);

        String name = normalizeName(request.getSemesterName());
        validateDates(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationStart(),
                request.getRegistrationEnd());

        if (semesterRepository.existsBySemesterNameAndIdNot(name, id)) {
            throw new BaseException(ErrorCode.DUPLICATE_SEMESTER_NAME);
        }

        checkOverlaps(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationStart(),
                request.getRegistrationEnd(),
                id);

        semester.setSemesterName(name);
        semester.setSemesterCode(toCode(name));
        semester.setStartDate(request.getStartDate());
        semester.setEndDate(request.getEndDate());
        semester.setRegistrationStart(request.getRegistrationStart());
        semester.setRegistrationEnd(request.getRegistrationEnd());
        semester.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        Semester saved = semesterRepository.saveAndFlush(semester);

        logChange("Cập nhật", saved);
        return toResponse(saved, today);
    }

    @Transactional(readOnly = true)
    public SemesterResponse getSemesterById(Long id) {
        Semester semester = semesterRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.SEMESTER_NOT_FOUND));
        return toResponse(semester, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public PageResponse<SemesterResponse> searchSemesters(SemesterSearchRequest request) {
        LocalDate today = LocalDate.now(clock);
        var pageable =
                PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "startDate", "id"));
        var semesters = semesterRepository.findAll(SemesterSpecification.search(request, today), pageable);
        return PageMapper.toPageResponse(semesters.map(semester -> toResponse(semester, today)));
    }

    private Semester getForUpdate(Long id) {
        return semesterRepository
                .findForUpdateByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BaseException(ErrorCode.SEMESTER_NOT_FOUND));
    }

    private void checkNotEnded(Semester semester, LocalDate today) {
        if (today.isAfter(semester.getEndDate())) {
            throw new BaseException(ErrorCode.SEMESTER_ENDED);
        }
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

    private void validateDates(LocalDate start, LocalDate end, LocalDate registrationStart, LocalDate registrationEnd) {
        if (start == null || end == null || registrationStart == null || registrationEnd == null) {
            throw new BaseException(ErrorCode.VALIDATION_ERROR);
        }
        if (!start.isBefore(end)) {
            throw new BaseException(ErrorCode.INVALID_SEMESTER_DATES);
        }
        if (!registrationStart.isBefore(registrationEnd) || !registrationEnd.isBefore(start)) {
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
        SemesterStatus status = today.isBefore(semester.getStartDate())
                ? SemesterStatus.UPCOMING
                : today.isAfter(semester.getEndDate()) ? SemesterStatus.ENDED : SemesterStatus.ONGOING;
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
