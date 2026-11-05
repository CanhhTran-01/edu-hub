package com.eduhub.edu_hub.people.application.admission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BalancedClassAssignmentStrategy implements ClassAssignmentStrategy {
    @Override
    public AssignmentResult assign(List<AdmissionCandidate> candidates, List<ClassState> classes) {
        List<String> capacityWarnings = new ArrayList<>();
        List<String> duplicateWarnings = new ArrayList<>();
        Map<Long, MutableState> states = new HashMap<>();
        classes.stream().sorted(Comparator.comparing(state -> state.administrativeClass().getClassName()))
                .forEach(state -> states.put(state.administrativeClass().getId(), new MutableState(state)));
        if (states.values().stream().mapToInt(MutableState::freeCapacity).sum() < candidates.size()) {
            capacityWarnings.add("Tổng sức chứa tối đa không đủ cho hồ sơ hợp lệ");
        }
        candidates.stream().sorted(Comparator.comparing(AdmissionCandidate::getEntranceScore).reversed()
                        .thenComparing(AdmissionCandidate::getApplicationCode))
                .forEach(candidate -> states.values().stream().filter(state -> state.freeCapacity() > 0)
                        .min(Comparator.comparingInt((MutableState state) -> state.hasName(candidate.getFullName()) ? 1 : 0)
                                .thenComparingInt(MutableState::size)
                                .thenComparing(state -> state.averageAfter(candidate.getEntranceScore()))
                                .thenComparing(MutableState::name))
                        .ifPresent(state -> { state.add(candidate); candidate.setAssignedClassId(state.id()); }));
        states.values().stream().sorted(Comparator.comparing(MutableState::name)).forEach(state -> {
            if (state.size() < state.min()) {
                capacityWarnings.add("Lớp " + state.name() + " dưới sức chứa tối thiểu (" + state.size() + "/" + state.min() + ")");
            }
            state.duplicateNames().forEach(name -> duplicateWarnings.add("Lớp " + state.name() + " có trùng họ tên: " + name));
        });
        return new AssignmentResult(duplicateWarnings, capacityWarnings);
    }

    private static final class MutableState {
        private final ClassState source;
        private final List<AdmissionCandidate> assigned = new ArrayList<>();
        private final Set<String> existingNames = new HashSet<>();
        private BigDecimal scoreSum = BigDecimal.ZERO;
        private int scoreCount;
        private MutableState(ClassState source) {
            this.source = source;
            source.existingStudents().forEach(student -> {
                existingNames.add(normalize(student.getFullName()));
                if (student.getEntranceScore() != null) { scoreSum = scoreSum.add(student.getEntranceScore()); scoreCount++; }
            });
        }
        private long id() {
            return source.administrativeClass().getId();
        }
        private String name() { return source.administrativeClass().getClassName(); }
        private int min() { return source.administrativeClass().getMinCapacity(); }
        private int size() { return source.existingStudents().size() + assigned.size(); }
        private int freeCapacity() { return source.administrativeClass().getMaxCapacity() - size(); }
        private boolean hasName(String fullName) {
            String name = normalize(fullName);
            return existingNames.contains(name) || assigned.stream().anyMatch(value -> normalize(value.getFullName()).equals(name));
        }
        private BigDecimal averageAfter(BigDecimal score) {
            return scoreSum.add(score).divide(BigDecimal.valueOf(scoreCount + 1L), 6, RoundingMode.HALF_UP);
        }
        private void add(AdmissionCandidate candidate) { assigned.add(candidate); scoreSum = scoreSum.add(candidate.getEntranceScore()); scoreCount++; }
        private Set<String> duplicateNames() {
            Set<String> seen = new HashSet<>(existingNames); Set<String> duplicates = new HashSet<>();
            for (AdmissionCandidate candidate : assigned) if (!seen.add(normalize(candidate.getFullName()))) duplicates.add(candidate.getFullName());
            return duplicates;
        }
        private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    }
}
