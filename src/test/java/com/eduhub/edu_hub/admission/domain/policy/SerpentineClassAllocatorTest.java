package com.eduhub.edu_hub.admission.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionApplicantStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionApplicant;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlanClass;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
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
        allocator.allocateByScore(rows, slots);
        assertThat(rows.stream()
                        .filter(row -> row.getAdministrativeClassId().equals(1L))
                        .toList())
                .hasSize(4);
        assertThat(rows.stream()
                        .filter(row -> row.getAdministrativeClassId().equals(2L))
                        .toList())
                .hasSize(3);
        assertThat(rows.stream()
                        .filter(row -> row.getAdministrativeClassId().equals(3L))
                        .toList())
                .hasSize(3);
        assertThat(rows.subList(0, 6))
                .extracting(AdmissionApplicant::getAdministrativeClassId)
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
        allocator.allocateByScore(rows, slots);
        assertThat(rows)
                .extracting(AdmissionApplicant::getAdministrativeClassId)
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
        allocator.allocateByScore(rows, slots);
        assertThat(rows.getLast().getAdministrativeClassId()).isEqualTo(2L);
        assertThat(rows.get(6).getAdministrativeClassId()).isEqualTo(1L);
        for (var slot : slots) {
            var assigned = rows.stream()
                    .filter(row -> row.getAdministrativeClassId().equals(slot.getAdministrativeClassId()))
                    .toList();
            assertThat(assigned).hasSize(4);
            assertThat(assigned.stream()
                            .collect(java.util.stream.Collectors.groupingBy(
                                    row -> TextsNormalizer.normalizeKey(row.getFullName()),
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
        allocator.allocateByScore(rows, slots(4, 4));
        assertThat(rows).allMatch(row -> row.getAdministrativeClassId() != null);
        assertThat(rows.stream()
                        .map(AdmissionApplicant::getAdministrativeClassId)
                        .distinct()
                        .count())
                .isEqualTo(2);
        assertThat(rows.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                AdmissionApplicant::getAdministrativeClassId, java.util.stream.Collectors.counting()))
                        .values())
                .containsExactlyInAnyOrder(4L, 4L);
    }

    @Test
    void sortsByVietnameseGivenNameBeforeFamilyNameForCodeAllocation() {
        var names = new ArrayList<>(List.of("Bùi Văn Cường", "Trần Thị Bình", "Nguyễn Văn An"));
        names.sort(TextsNormalizer.vietnameseNameOrder());
        assertThat(names).containsExactly("Nguyễn Văn An", "Trần Thị Bình", "Bùi Văn Cường");
    }

    @Test
    void neverUsesEnrolledStudentAsSwapCandidate() {
        var slots = slots(4, 0);
        var enrolled = enrolled("OLD", "Lê Văn Bình", 26, slots.get(1).getAdministrativeClassId());
        var rows = List.of(
                row("A", "Nguyễn Văn An", 30),
                row("B", "Nguyễn Văn An", 29),
                row("C", "Nguyễn Văn An", 28),
                row("D", "Nguyễn Văn An", 27));
        allocator.allocateByScore(rows, slots, List.of(enrolled));
        assertThat(enrolled.getAdministrativeClassId()).isEqualTo(slots.get(1).getAdministrativeClassId());
        assertThat(enrolled.getState()).isEqualTo(AdmissionApplicantStatus.ENROLLED);
        assertThat(rows).allMatch(row -> row.getAdministrativeClassId()
                .equals(slots.getFirst().getAdministrativeClassId()));
    }

    @Test
    void selectsPendingCandidateEvenWhenEnrolledCandidateHasCloserScore() {
        var slots = slots(4, 1);
        var enrolled = enrolled("OLD", "Lê Văn Cường", 26, slots.get(1).getAdministrativeClassId());
        var pendingCandidate = row("B", "Trần Văn Bình", 29);
        var rows = List.of(
                row("A", "Nguyễn Văn An", 30),
                pendingCandidate,
                row("C", "Nguyễn Văn An", 28),
                row("D", "Nguyễn Văn An", 27),
                row("E", "Nguyễn Văn An", 26));
        allocator.allocateByScore(rows, slots, List.of(enrolled));
        assertThat(enrolled.getAdministrativeClassId()).isEqualTo(slots.get(1).getAdministrativeClassId());
        assertThat(pendingCandidate.getAdministrativeClassId())
                .isEqualTo(slots.getFirst().getAdministrativeClassId());
        assertThat(rows.getLast().getAdministrativeClassId())
                .isEqualTo(slots.get(1).getAdministrativeClassId());
    }

    @Test
    void neverMovesEnrolledStudentEvenWhenItsNameAlreadyExceedsLimit() {
        var slots = slots(0, 1);
        var enrolled = List.of(
                enrolled("OLD-A", "Nguyễn Văn An", 30, slots.getFirst().getAdministrativeClassId()),
                enrolled("OLD-B", "Nguyễn Văn An", 29, slots.getFirst().getAdministrativeClassId()),
                enrolled("OLD-C", "Nguyễn Văn An", 28, slots.getFirst().getAdministrativeClassId()),
                enrolled("OLD-D", "Nguyễn Văn An", 27, slots.getFirst().getAdministrativeClassId()));
        var pending = row("NEW", "Trần Văn Bình", 26);
        allocator.allocateByScore(List.of(pending), slots, enrolled);
        assertThat(enrolled).allMatch(row -> row.getAdministrativeClassId()
                .equals(slots.getFirst().getAdministrativeClassId()));
        assertThat(pending.getAdministrativeClassId()).isEqualTo(slots.get(1).getAdministrativeClassId());
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
                enrolled("OLD-A", "Nguyễn Văn An", 25, slots.get(1).getAdministrativeClassId()),
                enrolled("OLD-B", "Nguyễn Văn An", 24, slots.get(1).getAdministrativeClassId()),
                enrolled("OLD-C", "Nguyễn Văn An", 23, slots.get(1).getAdministrativeClassId()));
        allocator.allocateByScore(rows, slots, enrolled);
        assertThat(rows.getLast().getAdministrativeClassId())
                .isEqualTo(slots.getFirst().getAdministrativeClassId());
        assertThat(rows.get(1).getAdministrativeClassId())
                .isEqualTo(slots.get(1).getAdministrativeClassId());
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
                enrolled("OLD-A", "Trần Văn Bình", 25, slots.getFirst().getAdministrativeClassId()),
                enrolled("OLD-B", "Trần Văn Bình", 24, slots.getFirst().getAdministrativeClassId()),
                enrolled("OLD-C", "Trần Văn Bình", 23, slots.getFirst().getAdministrativeClassId()));
        allocator.allocateByScore(rows, slots, enrolled);
        assertThat(rows.getLast().getAdministrativeClassId())
                .isEqualTo(slots.getFirst().getAdministrativeClassId());
        assertThat(rows.get(1).getAdministrativeClassId())
                .isEqualTo(slots.get(1).getAdministrativeClassId());
    }

    @Test
    void breaksEqualScoreTiesByFullNameInsteadOfApplicationCode() {
        var rows = List.of(row("A", "Trần Văn Bình", 25), row("Z", "Nguyễn Văn An", 25));
        allocator.allocateByScore(rows, slots(1, 1));
        assertThat(rows)
                .extracting(AdmissionApplicant::getAdministrativeClassId)
                .containsExactly(2L, 1L);
    }

    private AdmissionApplicant enrolled(String code, String name, int score, Long target) {
        var value = row(code, name, score);
        value.setState(AdmissionApplicantStatus.ENROLLED);
        value.setAdministrativeClassId(target);
        return value;
    }

    private List<AdmissionPlanClass> slots(int... sizes) {
        List<AdmissionPlanClass> slots = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            Long target = (long) i + 1;
            AdmissionPlanClass slot = AdmissionPlanClass.builder().build();
            slot.setAdministrativeClassId(target);
            slot.setTargetSize(sizes[i]);
            slots.add(slot);
        }
        return slots;
    }

    private AdmissionApplicant row(String code, String name, int score) {
        AdmissionApplicant row = AdmissionApplicant.builder().build();
        row.setApplicationCode(code);
        row.setFullName(name);
        row.setEntranceScore(BigDecimal.valueOf(score));
        return row;
    }
}
