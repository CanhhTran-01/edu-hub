package com.eduhub.edu_hub.scheduling.application.facade.impl;

import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.scheduling.api.mapper.SemesterResponseMapper;
import com.eduhub.edu_hub.scheduling.application.exception.SemesterException;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.scheduling.domain.repository.SemesterRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicCalendarFacadeImpl implements SchedulingFacade.AcademicCalendar {
    private final SemesterRepository semesterRepository;
    private final Clock clock;

    @Override
    public Optional<SemesterResponse> findCurrentSemester() {
        var today = LocalDate.now(clock);
        var candidates = semesterRepository.findCurrentSemesterCandidates(today);
        // Ưu tiên kỳ đang học; nếu đang giữa hai kỳ thì dùng kỳ đang trong khoảng đăng ký, không chọn kỳ tùy ý.
        return candidates.stream()
                .filter(semester -> !today.isBefore(semester.getStartDate()) && !today.isAfter(semester.getEndDate()))
                .findFirst()
                .or(() -> candidates.stream().findFirst())
                .map(semester -> SemesterResponseMapper.toResponse(semester, today));
    }

    @Override
    public List<SemesterResponse> findSemestersByIds(Set<Long> semesterIds) {
        if (semesterIds.isEmpty()) return List.of();
        return semesterRepository.findAllByIdInAndDeletedFalseOrderByStartDateDescIdDesc(semesterIds).stream()
                .map(semester -> SemesterResponseMapper.toResponse(semester, LocalDate.now(clock)))
                .toList();
    }

    @Override
    public SemesterResponse findSemester(Long semesterId) {
        return SemesterResponseMapper.toResponse(
                semesterRepository
                        .findByIdAndDeletedFalse(semesterId)
                        .orElseThrow(() -> new SemesterException(ErrorCode.SEMESTER_NOT_FOUND)),
                LocalDate.now(clock));
    }
}
