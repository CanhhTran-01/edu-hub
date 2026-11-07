package com.eduhub.edu_hub.people.application.admission;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.people.domain.model.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class SerpentineClassAllocatorTest {
    private final SerpentineClassAllocator allocator = new SerpentineClassAllocator();

    @Test
    void assignsInBothDirectionsAndKeepsRemainderInFirstClass() {
        var slots = slots(4, 3, 3);
        var rows = new ArrayList<AdmissionApplicant>();
        for (int i = 0; i < 10; i++) rows.add(row("HS" + i, "Sinh viên " + i, 30 - i));
        allocator.assign(rows, slots);
        assertThat(rows.stream()
                        .filter(row -> row.getAdministrativeClass().getId().equals(1L))
                        .toList())
                .hasSize(4);
        assertThat(rows.stream()
                        .filter(row -> row.getAdministrativeClass().getId().equals(2L))
                        .toList())
                .hasSize(3);
        assertThat(rows.stream()
                        .filter(row -> row.getAdministrativeClass().getId().equals(3L))
                        .toList())
                .hasSize(3);
        assertThat(rows.subList(0, 6))
                .extracting(row -> row.getAdministrativeClass().getId())
                .containsExactly(1L, 2L, 3L, 3L, 2L, 1L);
    }

    @Test
    void keepsThreeNormalizedSameNamesWithoutSwapping() {
        var slots = slots(3, 3);
        var rows = List.of(
                row("A", "Nguyễn Văn An", 30),
                row("B", "Trần Văn Bình", 29),
                row("C", "Lê Văn Cường", 28),
                row("D", "  NGUYỄN   VĂN AN ", 27),
                row("E", "Nguyễn Văn An", 26),
                row("F", "Phạm Văn Dũng", 25));
        allocator.assign(rows, slots);
        assertThat(rows)
                .extracting(row -> row.getAdministrativeClass().getId())
                .containsExactly(1L, 2L, 2L, 1L, 1L, 2L);
    }

    @Test
    void swapsFourthSameNameWithClosestScoreWhileKeepingBothClassesWithinNameLimit() {
        var slots = slots(4, 4);
        var rows = List.of(
                row("A", "Nguyễn Văn An", 30),
                row("B", "Trần Văn Bình", 29),
                row("C", "Lê Văn Cường", 28),
                row("D", "Nguyễn Văn An", 27),
                row("E", "Nguyễn Văn An", 26),
                row("F", "Phạm Văn Dũng", 25),
                row("G", "Vũ Văn Em", 24),
                row("H", "  NGUYỄN   VĂN AN ", 23));
        allocator.assign(rows, slots);
        assertThat(rows.getLast().getAdministrativeClass().getId()).isEqualTo(2L);
        assertThat(rows.get(6).getAdministrativeClass().getId()).isEqualTo(1L);
        for (var slot : slots) {
            var assigned = rows.stream()
                    .filter(row -> row.getAdministrativeClass().equals(slot.getAdministrativeClass()))
                    .toList();
            assertThat(assigned).hasSize(4);
            assertThat(assigned.stream()
                            .collect(java.util.stream.Collectors.groupingBy(
                                    row -> AdmissionText.key(row.getFullName()),
                                    java.util.stream.Collectors.counting()))
                            .values())
                    .allMatch(count -> count <= 3);
        }
    }

    @Test
    void keepsUnresolvableNamesAsSoftCollisionsAndTerminates() {
        var rows = List.of(
                row("A", "Nguyễn An", 30),
                row("B", "Nguyễn An", 29),
                row("C", "Nguyễn An", 28),
                row("D", "Nguyễn An", 27),
                row("E", "Nguyễn An", 26),
                row("F", "Nguyễn An", 25),
                row("G", "Nguyễn An", 24),
                row("H", "Nguyễn An", 23));
        allocator.assign(rows, slots(4, 4));
        assertThat(rows).allMatch(row -> row.getAdministrativeClass() != null);
        assertThat(rows.stream()
                        .map(row -> row.getAdministrativeClass().getId())
                        .distinct()
                        .count())
                .isEqualTo(2);
        assertThat(rows.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                row -> row.getAdministrativeClass().getId(), java.util.stream.Collectors.counting()))
                        .values())
                .containsExactlyInAnyOrder(4L, 4L);
    }

    @Test
    void sortsByVietnameseGivenNameBeforeFamilyNameForCodeAllocation() {
        var names = new ArrayList<>(List.of("Bùi Văn Cường", "Trần Thị Bình", "Nguyễn Văn An"));
        names.sort(AdmissionText.nameOrder());
        assertThat(names).containsExactly("Nguyễn Văn An", "Trần Thị Bình", "Bùi Văn Cường");
    }

    @Test
    void neverUsesEnrolledStudentAsSwapCandidate() {
        var slots = slots(4, 0);
        var enrolled = enrolled("OLD", "Lê Văn Bình", 26, slots.get(1).getAdministrativeClass());
        var rows = List.of(
                row("A", "Nguyễn Văn An", 30),
                row("B", "Nguyễn Văn An", 29),
                row("C", "Nguyễn Văn An", 28),
                row("D", "Nguyễn Văn An", 27));
        allocator.assign(rows, slots, List.of(enrolled));
        assertThat(enrolled.getAdministrativeClass()).isSameAs(slots.get(1).getAdministrativeClass());
        assertThat(enrolled.getState()).isEqualTo(AdmissionApplicant.State.ENROLLED);
        assertThat(rows)
                .allMatch(
                        row -> row.getAdministrativeClass() == slots.getFirst().getAdministrativeClass());
    }

    @Test
    void selectsPendingCandidateEvenWhenEnrolledCandidateHasCloserScore() {
        var slots = slots(4, 1);
        var enrolled = enrolled("OLD", "Lê Văn Cường", 26, slots.get(1).getAdministrativeClass());
        var pendingCandidate = row("B", "Trần Văn Bình", 29);
        var rows = List.of(
                row("A", "Nguyễn Văn An", 30),
                pendingCandidate,
                row("C", "Nguyễn Văn An", 28),
                row("D", "Nguyễn Văn An", 27),
                row("E", "Nguyễn Văn An", 26));
        allocator.assign(rows, slots, List.of(enrolled));
        assertThat(enrolled.getAdministrativeClass()).isSameAs(slots.get(1).getAdministrativeClass());
        assertThat(pendingCandidate.getAdministrativeClass())
                .isSameAs(slots.getFirst().getAdministrativeClass());
        assertThat(rows.getLast().getAdministrativeClass())
                .isSameAs(slots.get(1).getAdministrativeClass());
    }

    @Test
    void neverMovesEnrolledStudentEvenWhenItsNameAlreadyExceedsLimit() {
        var slots = slots(0, 1);
        var enrolled = List.of(
                enrolled("OLD-A", "Nguyễn Văn An", 30, slots.getFirst().getAdministrativeClass()),
                enrolled("OLD-B", "Nguyễn Văn An", 29, slots.getFirst().getAdministrativeClass()),
                enrolled("OLD-C", "Nguyễn Văn An", 28, slots.getFirst().getAdministrativeClass()),
                enrolled("OLD-D", "Nguyễn Văn An", 27, slots.getFirst().getAdministrativeClass()));
        var pending = row("NEW", "Trần Văn Bình", 26);
        allocator.assign(List.of(pending), slots, enrolled);
        assertThat(enrolled)
                .allMatch(
                        row -> row.getAdministrativeClass() == slots.getFirst().getAdministrativeClass());
        assertThat(pending.getAdministrativeClass()).isSameAs(slots.get(1).getAdministrativeClass());
    }

    @Test
    void doesNotSendFourthSameNameToNeighborAlreadyContainingThree() {
        var slots = slots(4, 1);
        var rows = List.of(
                row("A", "Nguyễn Văn An", 30),
                row("B", "Trần Văn Bình", 29),
                row("C", "Nguyễn Văn An", 28),
                row("D", "Nguyễn Văn An", 27),
                row("E", "Nguyễn Văn An", 26));
        var enrolled = List.of(
                enrolled("OLD-A", "Nguyễn Văn An", 25, slots.get(1).getAdministrativeClass()),
                enrolled("OLD-B", "Nguyễn Văn An", 24, slots.get(1).getAdministrativeClass()),
                enrolled("OLD-C", "Nguyễn Văn An", 23, slots.get(1).getAdministrativeClass()));
        allocator.assign(rows, slots, enrolled);
        assertThat(rows.getLast().getAdministrativeClass())
                .isSameAs(slots.getFirst().getAdministrativeClass());
        assertThat(rows.get(1).getAdministrativeClass()).isSameAs(slots.get(1).getAdministrativeClass());
    }

    @Test
    void doesNotIntroduceFourthSameNameIntoSourceClass() {
        var slots = slots(4, 1);
        var rows = List.of(
                row("A", "Nguyễn Văn An", 30),
                row("B", "Trần Văn Bình", 29),
                row("C", "Nguyễn Văn An", 28),
                row("D", "Nguyễn Văn An", 27),
                row("E", "Nguyễn Văn An", 26));
        var enrolled = List.of(
                enrolled("OLD-A", "Trần Văn Bình", 25, slots.getFirst().getAdministrativeClass()),
                enrolled("OLD-B", "Trần Văn Bình", 24, slots.getFirst().getAdministrativeClass()),
                enrolled("OLD-C", "Trần Văn Bình", 23, slots.getFirst().getAdministrativeClass()));
        allocator.assign(rows, slots, enrolled);
        assertThat(rows.getLast().getAdministrativeClass())
                .isSameAs(slots.getFirst().getAdministrativeClass());
        assertThat(rows.get(1).getAdministrativeClass()).isSameAs(slots.get(1).getAdministrativeClass());
    }

    @Test
    void breaksEqualScoreTiesByFullNameInsteadOfApplicationCode() {
        var rows = List.of(row("A", "Trần Văn Bình", 25), row("Z", "Nguyễn Văn An", 25));
        allocator.assign(rows, slots(1, 1));
        assertThat(rows).extracting(row -> row.getAdministrativeClass().getId()).containsExactly(2L, 1L);
    }

    private AdmissionApplicant enrolled(String code, String name, int score, AdministrativeClass target) {
        var value = row(code, name, score);
        value.setState(AdmissionApplicant.State.ENROLLED);
        value.setAdministrativeClass(target);
        return value;
    }

    private List<AdmissionPlanClass> slots(int... sizes) {
        List<AdmissionPlanClass> slots = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            AdministrativeClass target = new AdministrativeClass();
            target.setId((long) i + 1);
            AdmissionPlanClass slot = new AdmissionPlanClass();
            slot.setAdministrativeClass(target);
            slot.setTargetSize(sizes[i]);
            slots.add(slot);
        }
        return slots;
    }

    private AdmissionApplicant row(String code, String name, int score) {
        AdmissionApplicant row = new AdmissionApplicant();
        row.setApplicationCode(code);
        row.setFullName(name);
        row.setEntranceScore(BigDecimal.valueOf(score));
        return row;
    }
}
