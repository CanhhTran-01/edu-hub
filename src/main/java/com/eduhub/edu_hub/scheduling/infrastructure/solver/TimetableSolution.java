package com.eduhub.edu_hub.scheduling.infrastructure.solver;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.solution.*;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.score.HardSoftScore;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableAllocation;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableProblem;
import com.eduhub.edu_hub.scheduling.domain.policy.TimetablePolicy;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.*;

/** Mô hình kỹ thuật Timefold; không dùng entity JPA làm biến quy hoạch. */
@PlanningSolution
@Getter
@Setter
@NoArgsConstructor
public class TimetableSolution {
    @ProblemFactProperty
    private TimetableProblem problem; // dữ kiện cố định

    @PlanningEntityProperty
    private PhaseBoundary boundary; // ranh giới giữa 2 giai đoạn

    // Các danh sách entity mà solver thay đổi
    @PlanningEntityCollectionProperty
    private List<SubjectAssignment> subjects;

    @PlanningEntityCollectionProperty
    private List<ClassAssignment> classes;

    @PlanningEntityCollectionProperty
    private List<SessionAssignment> sessions;

    @PlanningScore
    private HardSoftScore score; // điểm HardSoftScore, tương ứng hardPenalty/softPenalty

    /** Tuần tương đối giúp đổi đợt hoặc ranh giới đợt mà vẫn dịch đồng thời mọi buổi liên quan. */
    public record RelativeSlot(int weekOffset, int dayOffset, int shift) {}

    /** Đổi lời giải thành kết quả. */
    public TimetableAllocation toAllocation() {

        LocalDate firstMonday = TimetablePolicy.firstTeachingMonday(problem.startDate());
        LocalDate phaseTwoStart = subjects.size() > 1 && boundary.getPhaseOneWeeks() != null
                ? firstMonday.plusWeeks(boundary.getPhaseOneWeeks() + TimetablePolicy.EXAM_BUFFER_WEEKS)
                : null;

        // map môn → phase
        Map<Long, Integer> phasesBySubjectId = subjects.stream()
                .collect(Collectors.toMap(
                        SubjectAssignment::getSubjectId,
                        subject -> subject.getPhase() == null ? 0 : subject.getPhase()));

        // map lớp → ClassAssignment
        var classesById =
                classes.stream().collect(Collectors.toMap(ClassAssignment::getModuleClassId, Function.identity()));

        var placements = createSessionPlacements(firstMonday, phaseTwoStart, phasesBySubjectId, classesById);

        // Ngày kết thúc đợt 1:
        // - Có đợt 2       : lùi 15 ngày từ ngày bắt đầu đợt 2 (khoảng đệm phục vụ thi KTHP đợt 1)
        // - Chỉ có 1 đợt   : lấy ngày học muộn nhất, không có buổi nào thì dùng firstMonday
        LocalDate phaseOneEnd = phaseTwoStart == null
                ? placements.stream()
                        .map(TimetableAllocation.SessionPlacement::date)
                        .filter(Objects::nonNull)
                        .max(LocalDate::compareTo)
                        .orElse(firstMonday)
                : phaseTwoStart.minusDays(15);

        return new TimetableAllocation(
                firstMonday,
                phaseOneEnd,
                phaseTwoStart,
                subjects.stream()
                        .map(subject -> new TimetableAllocation.SubjectPhase(
                                subject.getSubjectId(), subject.getPhase() == null ? 0 : subject.getPhase()))
                        .toList(),
                placements);
    }

    @PlanningEntity
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhaseBoundary {

        // Giai đoạn 1 kéo dài bao nhiêu tuần

        @PlanningId
        private Long semesterId;

        @ValueRangeProvider(id = "phaseWeekRange")
        private List<Integer> allowedWeeks;

        @PlanningVariable(valueRangeProviderRefs = "phaseWeekRange")
        private Integer phaseOneWeeks;

        @PlanningPin
        private boolean pinned;
    }

    @PlanningEntity
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectAssignment {

        // Môn học thuộc giai đoạn 1 hay 2

        @PlanningId
        private Long subjectId;

        @ValueRangeProvider(id = "phaseRange")
        private List<Integer> allowedPhases;

        @PlanningVariable(valueRangeProviderRefs = "phaseRange")
        private Integer phase;

        @PlanningPin
        private boolean pinned;
    }

    @PlanningEntity
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassAssignment {

        // Lớp học phần do giảng viên nào dạy

        @PlanningId
        private Long moduleClassId;

        private Long subjectId;

        @ValueRangeProvider(id = "lecturerRange")
        private List<Long> eligibleLecturerIds;

        @PlanningVariable(valueRangeProviderRefs = "lecturerRange")
        private Long lecturerId;

        @PlanningPin
        private boolean pinned;
    }

    @PlanningEntity
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionAssignment {

        // Mỗi buổi học vào tuần/thứ/ca nào và phòng nào

        @PlanningId
        private String sessionId;

        private Long moduleClassId;
        private int sessionIndex;

        @ValueRangeProvider(id = "sessionSlotRange")
        private List<RelativeSlot> allowedSlots;

        @ValueRangeProvider(id = "sessionRoomRange")
        private List<Long> eligibleRoomIds;

        @PlanningVariable(valueRangeProviderRefs = "sessionSlotRange")
        private RelativeSlot slot;

        @PlanningVariable(valueRangeProviderRefs = "sessionRoomRange")
        private Long roomId;

        @PlanningPin
        private boolean pinned;
    }

    private List<TimetableAllocation.SessionPlacement> createSessionPlacements(
            LocalDate firstMonday,
            LocalDate phaseTwoStart,
            Map<Long, Integer> phasesBySubjectId,
            Map<Long, ClassAssignment> classesById) {
        // Danh sách buổi học kết quả (đã đổi sang ngày thực)
        List<TimetableAllocation.SessionPlacement> placements = new ArrayList<>();

        for (var session : sessions) {
            // Lớp của buổi này, dùng để lấy môn và giảng viên
            var classAssignment = classesById.get(session.getModuleClassId());

            // Mốc bắt đầu của đợt: môn đợt 1 tính từ firstMonday, còn lại tính từ phaseTwoStart
            LocalDate phaseStart =
                    phasesBySubjectId.get(classAssignment.getSubjectId()) == 1 ? firstMonday : phaseTwoStart;
            RelativeSlot slot = session.getSlot();

            // Ngày thực = mốc đợt + số tuần + số ngày lệch; null nếu chưa có mốc hoặc chưa gán slot
            LocalDate date = phaseStart == null || slot == null
                    ? null
                    : phaseStart.plusWeeks(slot.weekOffset()).plusDays(slot.dayOffset());

            placements.add(new TimetableAllocation.SessionPlacement(
                    session.getModuleClassId(),
                    session.getSessionIndex(),
                    classAssignment.getLecturerId(), // giảng viên lấy từ cấp lớp
                    session.getRoomId(),
                    date,
                    slot == null ? 0 : slot.shift())); // chưa gán slot thì ca = 0
        }

        return placements;
    }
}
