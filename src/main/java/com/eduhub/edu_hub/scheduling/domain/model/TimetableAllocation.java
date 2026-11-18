package com.eduhub.edu_hub.scheduling.domain.model;

import java.time.LocalDate;
import java.util.List;

/** Các quyết định của phương án; ngày học là ngày thực tế, mỗi phần tử là một ca ba tiết. */
public record TimetableAllocation(
        LocalDate phaseOneStart,
        LocalDate phaseOneEnd,
        LocalDate phaseTwoStart,
        List<SubjectPhase> subjects,
        List<SessionPlacement> sessions) {

    public record SubjectPhase(Long subjectId, int phase) {}

    public record SessionPlacement(
            Long moduleClassId, int sessionIndex, Long lecturerId, Long roomId, LocalDate date, int shift) {}
}
