package com.eduhub.edu_hub.academic.domain.policy;

import com.eduhub.edu_hub.academic.domain.enums.AcademicRequirementStatus;
import java.util.*;

/** Đối chiếu hai mốc đã đạt và duyệt đồ thị thuần; không truy vấn hoặc áp giả định lên điểm thật. */
public final class PrerequisiteImpactPolicy {
    private PrerequisiteImpactPolicy() {}

    public record Requirement(Long subjectId, Boolean passedBeforeSemester) {}

    public record DependencyPath(List<Long> subjectIds, AcademicRequirementStatus status) {}

    public record Analysis(
            AcademicRequirementStatus prerequisiteStatus,
            List<Requirement> prerequisites,
            boolean failureAssumptionApplicable,
            AcademicRequirementStatus impactStatus,
            List<DependencyPath> affectedPaths,
            boolean consistentGraph,
            boolean limited) {}

    public static Analysis analyze(
            Long subjectId,
            Map<Long, List<Long>> prerequisitesBySubjectId,
            Set<Long> passedBeforeSemester,
            Set<Long> passedThroughSemester,
            boolean historyComplete,
            boolean graphComplete,
            int maximumPaths,
            int maximumPathLength) {
        boolean consistent = isAcyclic(prerequisitesBySubjectId);
        boolean completeGraph = graphComplete && consistent;
        var requirements = prerequisitesBySubjectId.getOrDefault(subjectId, List.of()).stream()
                .distinct()
                .sorted()
                .map(prerequisiteId -> new Requirement(
                        prerequisiteId,
                        passedBeforeSemester.contains(prerequisiteId)
                                ? Boolean.TRUE
                                : historyComplete ? Boolean.FALSE : null))
                .toList();
        var entryStatus =
                requirements.stream().anyMatch(requirement -> Boolean.FALSE.equals(requirement.passedBeforeSemester()))
                        ? AcademicRequirementStatus.BLOCKED
                        : !completeGraph
                                        || requirements.stream()
                                                .anyMatch(requirement -> requirement.passedBeforeSemester() == null)
                                ? AcademicRequirementStatus.INSUFFICIENT_DATA
                                : AcademicRequirementStatus.SATISFIED;
        if (passedThroughSemester.contains(subjectId))
            return new Analysis(
                    entryStatus,
                    requirements,
                    false,
                    AcademicRequirementStatus.SATISFIED,
                    List.of(),
                    consistent,
                    false);
        if (!consistent)
            return new Analysis(
                    entryStatus,
                    requirements,
                    true,
                    AcademicRequirementStatus.INSUFFICIENT_DATA,
                    List.of(),
                    false,
                    false);

        var dependentsByPrerequisiteId = new TreeMap<Long, Set<Long>>();
        prerequisitesBySubjectId.forEach(
                (dependentId, prerequisites) -> prerequisites.forEach(prerequisiteId -> dependentsByPrerequisiteId
                        .computeIfAbsent(prerequisiteId, ignored -> new TreeSet<>())
                        .add(dependentId)));
        var pendingPaths = new ArrayDeque<List<Long>>();
        pendingPaths.add(List.of(subjectId));
        Set<Long> visited = new HashSet<>();
        visited.add(subjectId);
        var affectedPaths = new ArrayList<DependencyPath>();
        boolean limited = false;
        while (!pendingPaths.isEmpty()) {
            var path = pendingPaths.removeFirst();
            var dependents = dependentsByPrerequisiteId.getOrDefault(path.getLast(), Set.of());
            for (var dependentId : dependents) {
                // Kết quả đã đạt vẫn thỏa tiên quyết; không lan truyền giả định trượt xuyên qua môn đã hoàn thành.
                if (passedThroughSemester.contains(dependentId) || !visited.add(dependentId)) continue;
                if (affectedPaths.size() >= maximumPaths || path.size() >= maximumPathLength) {
                    limited = true;
                    continue;
                }
                var extendedPath = new ArrayList<>(path);
                extendedPath.add(dependentId);
                var immutablePath = List.copyOf(extendedPath);
                affectedPaths.add(new DependencyPath(
                        immutablePath,
                        historyComplete && completeGraph
                                ? AcademicRequirementStatus.BLOCKED
                                : AcademicRequirementStatus.INSUFFICIENT_DATA));
                pendingPaths.add(immutablePath);
            }
        }
        var impactStatus = !historyComplete || !completeGraph || limited
                ? AcademicRequirementStatus.INSUFFICIENT_DATA
                : affectedPaths.isEmpty() ? AcademicRequirementStatus.SATISFIED : AcademicRequirementStatus.BLOCKED;
        return new Analysis(entryStatus, requirements, true, impactStatus, List.copyOf(affectedPaths), true, limited);
    }

    public static boolean isAcyclic(Map<Long, List<Long>> prerequisitesBySubjectId) {
        var dependencyCounts = new HashMap<Long, Integer>();
        var dependentsById = new HashMap<Long, Set<Long>>();
        prerequisitesBySubjectId.forEach((subjectId, prerequisites) -> {
            dependencyCounts.putIfAbsent(subjectId, 0);
            for (var prerequisiteId : new HashSet<>(prerequisites)) {
                dependencyCounts.putIfAbsent(prerequisiteId, 0);
                dependencyCounts.merge(subjectId, 1, Integer::sum);
                dependentsById
                        .computeIfAbsent(prerequisiteId, ignored -> new HashSet<>())
                        .add(subjectId);
            }
        });
        var availableSubjects = new ArrayDeque<Long>();
        dependencyCounts.forEach((subjectId, count) -> {
            if (count == 0) availableSubjects.add(subjectId);
        });
        int visitedCount = 0;
        while (!availableSubjects.isEmpty()) {
            var subjectId = availableSubjects.removeFirst();
            visitedCount++;
            for (var dependentId : dependentsById.getOrDefault(subjectId, Set.of()))
                if (dependencyCounts.merge(dependentId, -1, Integer::sum) == 0) availableSubjects.add(dependentId);
        }
        return visitedCount == dependencyCounts.size();
    }
}
