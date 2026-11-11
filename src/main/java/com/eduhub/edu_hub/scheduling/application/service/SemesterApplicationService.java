package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.scheduling.api.dto.request.CreateSemesterRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.SemesterSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.UpdateSemesterRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.scheduling.api.mapper.SemesterResponseMapper;
import com.eduhub.edu_hub.scheduling.application.exception.SemesterConflictException;
import com.eduhub.edu_hub.scheduling.application.exception.SemesterException;
import com.eduhub.edu_hub.scheduling.application.specification.SemesterSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.policy.SemesterPolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class SemesterApplicationService {
    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;
    private final Clock clock;

    public SemesterApplicationService(
            SemesterRepository semesterRepository, ModuleClassRepository moduleClassRepository, Clock clock) {
        this.semesterRepository = semesterRepository;
        this.moduleClassRepository = moduleClassRepository;
        this.clock = clock;
    }

    @Transactional
    public SemesterResponse createSemester(CreateSemesterRequest request) {

        // chuẩn hóa lại tên kì học
        String semesterName = SemesterPolicy.normalizeSemesterName(request.getSemesterName());

        // vaidate nghiệp vụ
        SemesterPolicy.validateDates(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationStart(),
                request.getRegistrationEnd());

        // check trùng tên
        if (semesterRepository.existsBySemesterName(semesterName)) {
            throw new SemesterException(ErrorCode.DUPLICATE_SEMESTER_NAME);
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
                .semesterName(semesterName)
                .semesterCode(SemesterPolicy.generateSemesterCode(semesterName))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .registrationStart(request.getRegistrationStart())
                .registrationEnd(request.getRegistrationEnd())
                .build();

        LocalDateTime createdAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        semester.setCreatedAt(createdAt);
        semester.setUpdatedAt(createdAt);

        // ghi xuống database (ép ghi ngay)
        Semester savedSemester = semesterRepository.saveAndFlush(semester);
        logChange("Tạo", savedSemester);

        return SemesterResponseMapper.toResponse(savedSemester, LocalDate.now(clock));
    }

    @Transactional
    public SemesterResponse updateSemester(Long semesterId, UpdateSemesterRequest request) {

        Semester semester = semesterRepository
                .findForUpdateByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND));

        // Kì này end rồi thì không cho cập nhật ?
        LocalDate today = LocalDate.now(clock);
        if (today.isAfter(semester.getEndDate())) {
            throw new SemesterException(ErrorCode.SEMESTER_ENDED);
        }

        // chuẩn hóa tên
        String semesterName = SemesterPolicy.normalizeSemesterName(request.getSemesterName());

        // validate nghiệp vụ cho ngày
        SemesterPolicy.validateDates(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationStart(),
                request.getRegistrationEnd());

        // check trùng tên
        if (semesterRepository.existsBySemesterNameAndIdNot(semesterName, semesterId)) {
            throw new SemesterException(ErrorCode.DUPLICATE_SEMESTER_NAME);
        }

        // check khoảng thời gian chồng lấn
        checkOverlaps(
                request.getStartDate(),
                request.getEndDate(),
                request.getRegistrationStart(),
                request.getRegistrationEnd(),
                semesterId);

        // TODO: cần làm rõ tại đây, cho cập nhật khoảng date hay không ? chắc là không
        // check ngày thay đổi
        boolean datesChanged = !semester.getStartDate().equals(request.getStartDate())
                || !semester.getEndDate().equals(request.getEndDate());

        // TODO: đổi ngày đăng kí thì sao ? - ở use-case mở đăng ký

        // date chưa bị thay đổi, phải được xác nhận từ client, kì chưa có lớp hoặc có nhưng đã hủy thì mới update được
        if (datesChanged
                && !request.isConfirmDateChange()
                && moduleClassRepository.existsBySemester_IdAndStatusNot(semesterId, ModuleClassStatus.CANCELLED)) {
            throw new SemesterException(ErrorCode.SEMESTER_DATE_CHANGE_CONFIRMATION_REQUIRED);
        }

        // TODO(CLASS_SCHEDULE_INTEGRATION): chekc lịch đã xếp vẫn nằm trong khoảng ngày mới (nếu cho update date).

        // cập nhật field
        semester.setSemesterName(semesterName);
        semester.setSemesterCode(SemesterPolicy.generateSemesterCode(semesterName));
        semester.setStartDate(request.getStartDate());
        semester.setEndDate(request.getEndDate());
        semester.setRegistrationStart(request.getRegistrationStart());
        semester.setRegistrationEnd(request.getRegistrationEnd());
        semester.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        // ghi xuống database
        Semester savedSemester = semesterRepository.saveAndFlush(semester);
        logChange("Cập nhật", savedSemester);

        return SemesterResponseMapper.toResponse(savedSemester, today);
    }

    @Transactional(readOnly = true)
    public SemesterResponse getSemesterById(Long semesterId) {

        Semester semester = semesterRepository
                .findByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND));

        LocalDate today = LocalDate.now(clock);
        return SemesterResponseMapper.toResponse(semester, today);
    }

    @Transactional(readOnly = true)
    public PageResponse<SemesterResponse> searchSemesters(SemesterSearchRequest request) {

        LocalDate today = LocalDate.now(clock);

        var pageable =
                PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "startDate", "id"));

        var semesters = semesterRepository.findAll(SemesterSpecification.search(request, today), pageable);

        return PageMapper.toPageResponse(semesters.map(semester -> SemesterResponseMapper.toResponse(semester, today)));
    }

    private void checkOverlaps(
            LocalDate startDate,
            LocalDate endDate,
            LocalDate registrationStart,
            LocalDate registrationEnd,
            Long excludedSemesterId) {
        List<Semester> studyConflicts = semesterRepository.findAll(
                SemesterSpecification.overlappingStudyPeriod(startDate, endDate, excludedSemesterId), Sort.by("id"));
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
                SemesterSpecification.overlappingRegistrationPeriod(
                        registrationStart, registrationEnd, excludedSemesterId),
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
