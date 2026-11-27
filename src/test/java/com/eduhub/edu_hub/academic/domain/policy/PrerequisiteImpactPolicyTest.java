package com.eduhub.edu_hub.academic.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.academic.domain.enums.AcademicRequirementStatus;
import java.util.*;
import org.junit.jupiter.api.Test;

class PrerequisiteImpactPolicyTest {
    Map<Long, List<Long>> chain = Map.of(1L, List.of(), 2L, List.of(1L), 3L, List.of(2L));

    @Test
    void identifiesDirectAndIndirectPathsWithoutMutatingTheHistoryOrGraph() {
        var analysis = analyze(1L, Set.of(), Set.of(), true, true);
        assertThat(analysis.prerequisiteStatus()).isEqualTo(AcademicRequirementStatus.SATISFIED);
        assertThat(analysis.impactStatus()).isEqualTo(AcademicRequirementStatus.BLOCKED);
        assertThat(analysis.affectedPaths())
                .extracting(PrerequisiteImpactPolicy.DependencyPath::subjectIds)
                .containsExactly(List.of(1L, 2L), List.of(1L, 2L, 3L));
        assertThat(chain.get(2L)).containsExactly(1L);
    }

    @Test
    void alreadyPassedIntermediateCourseStopsTheFailurePropagation() {
        var analysis = analyze(1L, Set.of(2L), Set.of(2L), true, true);
        assertThat(analysis.affectedPaths()).isEmpty();
        assertThat(analysis.impactStatus()).isEqualTo(AcademicRequirementStatus.SATISFIED);
    }

    @Test
    void earlierPassingResultSurvivesTheAssumedFailureOfAnotherAttempt() {
        var analysis = analyze(1L, Set.of(1L), Set.of(1L), true, true);
        assertThat(analysis.failureAssumptionApplicable()).isFalse();
        assertThat(analysis.affectedPaths()).isEmpty();
    }

    @Test
    void passingInTheCurrentSemesterDoesNotRetroactivelyMeetItsEntryRequirements() {
        var analysis = analyze(2L, Set.of(), Set.of(1L), true, true);
        assertThat(analysis.prerequisites().getFirst().passedBeforeSemester()).isFalse();
        assertThat(analysis.prerequisiteStatus()).isEqualTo(AcademicRequirementStatus.BLOCKED);
    }

    @Test
    void missingHistoryKeepsUnknownDistinctFromNotPassedAndDoesNotClaimNoImpact() {
        var analysis = analyze(2L, Set.of(), Set.of(), false, true);
        assertThat(analysis.prerequisites().getFirst().passedBeforeSemester()).isNull();
        assertThat(analysis.prerequisiteStatus()).isEqualTo(AcademicRequirementStatus.INSUFFICIENT_DATA);
        assertThat(analysis.affectedPaths())
                .allSatisfy(path -> assertThat(path.status()).isEqualTo(AcademicRequirementStatus.INSUFFICIENT_DATA));
        assertThat(analyze(3L, Set.of(), Set.of(), true, false).impactStatus())
                .isEqualTo(AcademicRequirementStatus.INSUFFICIENT_DATA);
    }

    @Test
    void cyclicGraphDoesNotLoopOrProduceAFalseSatisfiedStatus() {
        var cyclic = Map.of(1L, List.of(2L), 2L, List.of(1L));
        var analysis = PrerequisiteImpactPolicy.analyze(1L, cyclic, Set.of(), Set.of(), true, true, 200, 64);
        assertThat(analysis.consistentGraph()).isFalse();
        assertThat(analysis.impactStatus()).isEqualTo(AcademicRequirementStatus.INSUFFICIENT_DATA);
    }

    @Test
    void aPathOrDepthLimitLeavesTheUnexaminedPartUnknown() {
        var limitedPaths = PrerequisiteImpactPolicy.analyze(1L, chain, Set.of(), Set.of(), true, true, 1, 64);
        assertThat(limitedPaths.limited()).isTrue();
        assertThat(limitedPaths.affectedPaths()).hasSize(1);
        assertThat(limitedPaths.impactStatus()).isEqualTo(AcademicRequirementStatus.INSUFFICIENT_DATA);
        var limitedDepth = PrerequisiteImpactPolicy.analyze(1L, chain, Set.of(), Set.of(), true, true, 200, 2);
        assertThat(limitedDepth.limited()).isTrue();
    }

    @Test
    void aSharedDescendantHasOneShortestExplanationAndDirectEdgesRemainDirect() {
        var graph = Map.of(1L, List.<Long>of(), 2L, List.of(1L), 3L, List.of(1L), 4L, List.of(1L, 2L, 3L));
        var analysis = PrerequisiteImpactPolicy.analyze(1L, graph, Set.of(), Set.of(), true, true, 200, 64);
        assertThat(analysis.affectedPaths())
                .extracting(PrerequisiteImpactPolicy.DependencyPath::subjectIds)
                .containsExactly(List.of(1L, 2L), List.of(1L, 3L), List.of(1L, 4L));
    }

    private PrerequisiteImpactPolicy.Analysis analyze(
            Long subjectId, Set<Long> before, Set<Long> through, boolean historyComplete, boolean graphComplete) {
        return PrerequisiteImpactPolicy.analyze(
                subjectId, chain, before, through, historyComplete, graphComplete, 200, 64);
    }
}
