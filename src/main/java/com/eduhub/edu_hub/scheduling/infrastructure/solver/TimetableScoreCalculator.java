package com.eduhub.edu_hub.scheduling.infrastructure.solver;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.calculator.EasyScoreCalculator;
import com.eduhub.edu_hub.scheduling.domain.policy.TimetablePolicy;

public class TimetableScoreCalculator implements EasyScoreCalculator<TimetableSolution, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(TimetableSolution solution) {
        var assessment = TimetablePolicy.assess(solution.getProblem(), solution.toAllocation(), false);
        return HardSoftScore.of(-assessment.hardPenalty(), -assessment.softPenalty());
    }
}
