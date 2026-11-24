package com.eduhub.edu_hub.scheduling.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.scheduling.TimetableTestData;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimetablePolicyTest {
    @Test
    void clipsRecurringSchedulesToSelectedWeekAndRetainsLegacySunday() {
        var sunday = new TimetableProblem.ExistingSession(
                1L, 100L, 201L, 501L, 8, 2, 4, LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
        assertThat(TimetablePolicy.occurrenceDatesBetween(
                        sunday, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22)))
                .containsExactly(LocalDate.of(2026, 11, 22));
        assertThat(TimetablePolicy.occurrenceDatesBetween(
                        sunday, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 21)))
                .isEmpty();
        assertThat(TimetablePolicy.occurrenceDatesBetween(sunday, LocalDate.of(2031, 1, 1), LocalDate.of(2031, 1, 7)))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"45,15", "46,16", "47,16", "48,16", "1,1", "2147483647,715827883"})
    void roundsSessionCountWithoutOverflow(int periods, int expected) {
        assertThat(TimetablePolicy.requiredSessions(periods)).isEqualTo(expected);
    }

    @Test
    void acceptsOddSubjectSplitAndFinalSingleSessionWeek() {
        var problem = TimetableTestData.problem(3, 1, 46);
        var assessment = TimetablePolicy.assess(problem, TimetableTestData.allocation(problem), true);
        assertThat(assessment.violations()).isEmpty();
        assertThat(assessment.isFeasible()).isTrue();
        assertThat(problem.classes().getFirst().totalTeachingPeriods()).isEqualTo(46);
    }

    @Test
    void startsOnMondayOnOrAfterSemesterStart() {
        assertThat(TimetablePolicy.firstTeachingMonday(LocalDate.of(2027, 8, 7)))
                .isEqualTo(LocalDate.of(2027, 8, 9));
        assertThat(TimetablePolicy.firstTeachingMonday(LocalDate.of(2027, 8, 9)))
                .isEqualTo(LocalDate.of(2027, 8, 9));
    }

    @Test
    void expandsOnlyActualWeekdayOccurrencesIncludingEmptyIntersection() {
        var schedule = new TimetableProblem.ExistingSession(
                1L, 1L, 201L, 501L, 2, 1, 3, LocalDate.of(2027, 8, 10), LocalDate.of(2027, 8, 15));
        assertThat(TimetablePolicy.occurrenceDates(schedule)).isEmpty();
        assertThat(TimetablePolicy.occursOn(schedule, LocalDate.of(2027, 8, 11)))
                .isFalse();
    }

    @Test
    void rejectsMissingSessionsAndDuplicateSessionIndexes() {
        var problem = TimetableTestData.problem(1, 1, 9);
        var allocation = TimetableTestData.allocation(problem);
        var sessions = new ArrayList<>(allocation.sessions());
        sessions.removeLast();
        assertInvalid(problem, replaceSessions(allocation, sessions), "Chưa đủ số buổi");
        sessions.add(sessions.getFirst());
        assertInvalid(problem, replaceSessions(allocation, sessions), "Chỉ số buổi");
    }

    @Test
    void rejectsRoomAndTeacherConflictsAndMultipleSessionsPerClassDay() {
        var problem = TimetableTestData.problem(1, 2, 6);
        var allocation = TimetableTestData.allocation(problem);
        var sessions = new ArrayList<>(allocation.sessions());
        var original = sessions.get(2);
        sessions.set(
                2,
                new TimetableAllocation.SessionPlacement(
                        original.moduleClassId(),
                        original.sessionIndex(),
                        201L,
                        501L,
                        sessions.getFirst().date(),
                        1));
        var assessment = TimetablePolicy.assess(problem, replaceSessions(allocation, sessions), true);
        assertThat(assessment.violations()).anyMatch(message -> message.contains("Trùng phòng"));
        assertThat(assessment.violations()).anyMatch(message -> message.contains("Trùng giảng viên"));
        var second = sessions.get(1);
        sessions.set(
                1,
                new TimetableAllocation.SessionPlacement(
                        second.moduleClassId(),
                        second.sessionIndex(),
                        201L,
                        502L,
                        sessions.getFirst().date(),
                        2));
        assertInvalid(problem, replaceSessions(allocation, sessions), "Một lớp chỉ học một ca/ngày");
    }

    @Test
    void preservesPenaltyTotalsAndDiagnosticOrderWhenManySessionsShareResources() {
        var problem = TimetableTestData.problem(1, 2, 6);
        var allocation = TimetableTestData.allocation(problem);
        var sessions = allocation.sessions().stream()
                .map(session -> new TimetableAllocation.SessionPlacement(
                        session.moduleClassId(), session.sessionIndex(), 201L, 501L, allocation.phaseOneStart(), 1))
                .toList();
        var conflictingAllocation = replaceSessions(allocation, sessions);

        // Bốn buổi cùng tài nguyên tạo mức phạt tích lũy; bật chẩn đoán không được làm đổi điểm.
        var explained = TimetablePolicy.assess(problem, conflictingAllocation, true);
        var unexplained = TimetablePolicy.assess(problem, conflictingAllocation, false);

        assertThat(explained.hardPenalty()).isEqualTo(14);
        assertThat(explained.softPenalty()).isEqualTo(4);
        assertThat(explained.violations())
                .containsExactly(
                        "Trùng phòng ngày 2027-08-09",
                        "Trùng giảng viên ngày 2027-08-09",
                        "Một lớp chỉ học một ca/ngày: MC_1_0",
                        "Một lớp chỉ học một ca/ngày: MC_1_1");
        assertThat(unexplained.hardPenalty()).isEqualTo(explained.hardPenalty());
        assertThat(unexplained.softPenalty()).isEqualTo(explained.softPenalty());
        assertThat(unexplained.violations()).isEmpty();
    }

    @Test
    void countsUndatedSessionsWithoutChargingMissingSessionOrResourceConflicts() {
        var problem = TimetableTestData.problem(1, 1, 6);
        var allocation = TimetableTestData.allocation(problem);
        var sessions = new ArrayList<>(allocation.sessions());
        var firstSession = sessions.getFirst();
        sessions.set(
                0,
                new TimetableAllocation.SessionPlacement(
                        firstSession.moduleClassId(), firstSession.sessionIndex(), 201L, 501L, null, 1));

        var assessment = TimetablePolicy.assess(problem, replaceSessions(allocation, sessions), true);

        assertThat(assessment.hardPenalty()).isEqualTo(1);
        assertThat(assessment.softPenalty()).isEqualTo(1);
        assertThat(assessment.violations()).containsExactly("Chưa phân công đủ ngày, phòng hoặc giảng viên: MC_1_0");
    }

    @Test
    void rejectsMissingMiddleWeekAndSingleSessionBeforeFinalWeek() {
        var problem = TimetableTestData.problem(1, 1, 18);
        var allocation = TimetableTestData.allocation(problem);
        var sessions = new ArrayList<>(allocation.sessions());
        for (int index = 3; index < 6; index++) {
            var session = sessions.get(index);
            sessions.set(
                    index,
                    new TimetableAllocation.SessionPlacement(
                            session.moduleClassId(),
                            index,
                            session.lecturerId(),
                            session.roomId(),
                            session.date().plusWeeks(1),
                            session.shift()));
        }
        assertInvalid(problem, replaceSessions(allocation, sessions), "Có tuần nghỉ giữa môn");
        var first = sessions.getFirst();
        var sparse = List.of(
                first,
                new TimetableAllocation.SessionPlacement(
                        first.moduleClassId(), 1, 201L, 501L, first.date().plusWeeks(1), 1));
        assertInvalid(problem, replaceSessions(allocation, sparse), "Thiếu hai ca/tuần");
    }

    @Test
    void consecutiveDaysAreSoftAndFourSessionsAreAllowed() {
        var problem = TimetableTestData.problem(1, 1, 12);
        var allocation = TimetableTestData.allocation(problem);
        List<TimetableAllocation.SessionPlacement> sessions = new ArrayList<>();
        int[] dayOffsets = {0, 1, 3, 5};
        for (int index = 0; index < 4; index++)
            sessions.add(new TimetableAllocation.SessionPlacement(
                    100L, index, 201L, 501L, allocation.phaseOneStart().plusDays(dayOffsets[index]), 1));
        var assessment = TimetablePolicy.assess(problem, replaceSessions(allocation, sessions), true);
        assertThat(assessment.isFeasible()).isTrue();
        assertThat(assessment.softPenalty()).isPositive();
    }

    @Test
    void checksCapacityAgainstMaximumAndRejectsWrongLecturer() {
        var problem = TimetableTestData.problem(1, 1, 6);
        var insufficient = new TimetableProblem(
                problem.semesterId(),
                problem.startDate(),
                problem.endDate(),
                problem.registrationStart(),
                problem.registrationEnd(),
                problem.classes(),
                List.of(),
                List.of(new TimetableProblem.RoomAvailability(501L, "101-TA1", 59)),
                List.of(),
                List.of(),
                Set.of(),
                null);
        var assessment = TimetablePolicy.assess(insufficient, TimetableTestData.allocation(problem), true);
        assertThat(assessment.violations()).anyMatch(message -> message.contains("Phòng không đủ"));
        assertThat(assessment.violations()).anyMatch(message -> message.contains("Giảng viên không đủ"));
    }

    @Test
    void excludesReplacedScheduleFromExternalConflictsButHonorsHistoricalLecturerIds() {
        var problem = TimetableTestData.problem(1, 1, 6);
        var allocation = TimetableTestData.allocation(problem);
        var existing = new TimetableProblem.ExistingSession(
                90L, 999L, 201L, 502L, 2, 1, 3, allocation.phaseOneStart(), allocation.phaseOneStart());
        var withExisting = new TimetableProblem(
                problem.semesterId(),
                problem.startDate(),
                problem.endDate(),
                problem.registrationStart(),
                problem.registrationEnd(),
                problem.classes(),
                problem.lecturers(),
                problem.rooms(),
                List.of(existing),
                List.of(),
                Set.of(),
                null);
        assertInvalid(withExisting, allocation, "Xung đột lịch đang có");
    }

    private void assertInvalid(TimetableProblem problem, TimetableAllocation allocation, String message) {
        var assessment = TimetablePolicy.assess(problem, allocation, true);
        assertThat(assessment.isFeasible()).isFalse();
        assertThat(assessment.violations()).anyMatch(violation -> violation.contains(message));
    }

    @Test
    void rejectsFifthWeeklySessionAndSunday() {
        var problem = TimetableTestData.problem(1, 1, 15);
        var allocation = TimetableTestData.allocation(problem);
        List<TimetableAllocation.SessionPlacement> sessions = new ArrayList<>();
        for (int index = 0; index < 5; index++)
            sessions.add(new TimetableAllocation.SessionPlacement(
                    100L, index, 201L, 501L, allocation.phaseOneStart().plusDays(index), 1));
        assertInvalid(problem, replaceSessions(allocation, sessions), "Vượt bốn ca/tuần");
        sessions.set(
                4,
                new TimetableAllocation.SessionPlacement(
                        100L, 4, 201L, 501L, allocation.phaseOneStart().plusDays(6), 1));
        assertInvalid(problem, replaceSessions(allocation, sessions), "Ngày hoặc ca không hợp lệ");
    }

    @Test
    void rejectsShortenedExamBuffer() {
        var problem = TimetableTestData.problem(2, 1, 6);
        var allocation = TimetableTestData.allocation(problem);
        var shortened = new TimetableAllocation(
                allocation.phaseOneStart(),
                allocation.phaseOneEnd(),
                allocation.phaseTwoStart().minusWeeks(1),
                allocation.subjects(),
                allocation.sessions());
        assertInvalid(problem, shortened, "Hai đợt phải cách nhau hai tuần lịch đầy đủ");
    }

    private TimetableAllocation replaceSessions(
            TimetableAllocation allocation, List<TimetableAllocation.SessionPlacement> sessions) {
        return new TimetableAllocation(
                allocation.phaseOneStart(),
                allocation.phaseOneEnd(),
                allocation.phaseTwoStart(),
                allocation.subjects(),
                sessions);
    }

    @Test
    void equalWeekdayAndShiftInDifferentPhasesDoNotOverlap() {
        var first = new TimetableProblem.ExistingSession(
                1L, 100L, 201L, 501L, 2, 1, 3, LocalDate.of(2027, 8, 9), LocalDate.of(2027, 8, 30));
        var laterPhase = new TimetableProblem.ExistingSession(
                2L, 101L, 202L, 502L, 2, 1, 3, LocalDate.of(2027, 9, 20), LocalDate.of(2027, 10, 11));
        assertThat(TimetablePolicy.firstOverlapDate(first, laterPhase)).isEmpty();
        var sameDay = new TimetableProblem.ExistingSession(
                3L, 102L, 202L, 502L, 2, 3, 5, LocalDate.of(2027, 8, 23), LocalDate.of(2027, 8, 23));
        assertThat(TimetablePolicy.firstOverlapDate(first, sameDay)).contains(LocalDate.of(2027, 8, 23));
    }
}
