package com.eduhub.edu_hub.people.application.admission;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BalancedClassAssignmentStrategyTest {
    private final BalancedClassAssignmentStrategy strategy = new BalancedClassAssignmentStrategy();

    @Test
    void balancesSizeAndScoreWithoutExceedingMaximum() {
        AdministrativeClass first = administrativeClass(1L, "A", 0, 2);
        AdministrativeClass second = administrativeClass(2L, "B", 0, 2);
        List<AdmissionCandidate> candidates = List.of(candidate("1", "An", "30"), candidate("2", "Bình", "20"),
                candidate("3", "Cường", "10"), candidate("4", "Dung", "0"));
        strategy.assign(candidates, List.of(new ClassAssignmentStrategy.ClassState(first, List.of()),
                new ClassAssignmentStrategy.ClassState(second, List.of())));
        assertThat(candidates).filteredOn(value -> value.getAssignedClassId().equals(1L)).hasSize(2);
        assertThat(candidates).filteredOn(value -> value.getAssignedClassId().equals(2L)).hasSize(2);
        BigDecimal firstSum = candidates.stream().filter(value -> value.getAssignedClassId().equals(1L))
                .map(AdmissionCandidate::getEntranceScore).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal secondSum = candidates.stream().filter(value -> value.getAssignedClassId().equals(2L))
                .map(AdmissionCandidate::getEntranceScore).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(firstSum.subtract(secondSum).abs()).isLessThanOrEqualTo(new BigDecimal("20"));
    }

    @Test
    void warnsWhenMinimumCannotBeReachedAndSeparatesDuplicateNames() {
        AdministrativeClass first = administrativeClass(1L, "A", 2, 2);
        AdministrativeClass second = administrativeClass(2L, "B", 2, 2);
        var result = strategy.assign(List.of(candidate("1", "Nguyễn An", "30"), candidate("2", "Nguyễn An", "20")),
                List.of(new ClassAssignmentStrategy.ClassState(first, List.of()), new ClassAssignmentStrategy.ClassState(second, List.of())));
        assertThat(result.capacityWarnings()).hasSize(2);
        assertThat(result.duplicateNameWarnings()).isEmpty();
    }

    private AdministrativeClass administrativeClass(long id, String name, int min, int max) {
        AdministrativeClass value = new AdministrativeClass(); value.setId(id); value.setClassName(name);
        value.setMinCapacity(min); value.setMaxCapacity(max); return value;
    }
    private AdmissionCandidate candidate(String code, String name, String score) {
        AdmissionCandidate value = new AdmissionCandidate(); value.setApplicationCode(code); value.setFullName(name);
        value.setEntranceScore(new BigDecimal(score)); return value;
    }
}
