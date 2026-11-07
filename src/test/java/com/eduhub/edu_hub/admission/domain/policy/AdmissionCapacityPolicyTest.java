package com.eduhub.edu_hub.admission.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.admission.domain.model.ClassDivisionScenario;
import com.eduhub.edu_hub.shared.exception.BaseException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdmissionCapacityPolicyTest {
    private final AdmissionCapacityPolicy policy = new AdmissionCapacityPolicy();

    @Test
    void offersEveryNormalScenarioAndOnlyRecommendsFewestClasses() {
        var scenarios = policy.scenarios(200, 40, 50, 15, 20);
        assertThat(scenarios).extracting(ClassDivisionScenario::classCount).containsExactly(4, 5);
        assertThat(scenarios.getFirst().targetSizes()).containsExactly(50, 50, 50, 50);
        assertThat(scenarios.getLast().targetSizes()).containsExactly(40, 40, 40, 40, 40);
        assertThat(scenarios.getFirst().recommended()).isTrue();
        assertThat(scenarios.getLast().recommended()).isFalse();
    }

    @Test
    void usesOneReserveSeatForOneHundredAndOneStudents() {
        var scenarios = policy.scenarios(101, 40, 50, 15, 2);
        var recommended = scenarios.getFirst();
        assertThat(recommended.targetSizes()).containsExactly(51, 50);
        assertThat(recommended.reserveUsed()).isEqualTo(1);
        assertThat(recommended.level()).isEqualTo("RESERVE");
        assertThat(scenarios.getLast().targetSizes()).containsExactly(34, 34, 33);
    }

    @Test
    void recommendsBelowMinimumWhenNeitherNormalNorReserveCanWork() {
        assertThat(policy.scenarios(70, 40, 50, 15, 2).getFirst().targetSizes()).containsExactly(35, 35);
        assertThat(policy.scenarios(30, 40, 50, 15, 1).getFirst().targetSizes()).containsExactly(30);
        assertThat(policy.scenarios(0, 40, 50, 15, 0)).isEmpty();
    }

    @Test
    void neverRecommendsReserveToReduceAnAlreadyFeasibleNormalClassCount() {
        var scenarios = policy.scenarios(120, 40, 50, 15, 10);
        assertThat(scenarios).hasSize(1);
        assertThat(scenarios.getFirst().classCount()).isEqualTo(3);
    }

    @Test
    void warnsAboutRoomsEvenWhenNoReserveSeatsAreCurrentlyUsed() {
        var scenario = policy.scenarios(200, 40, 50, 15, 3).getFirst();
        assertThat(scenario.requiredRoomCapacity()).isEqualTo(65);
        assertThat(scenario.insufficientRooms()).isTrue();
    }

    @Test
    void requiresExplicitReserveAcceptanceAndReasonBelowMinimum() {
        var reserve = policy.scenarios(101, 40, 50, 15, 20).getFirst();
        assertThatThrownBy(() -> policy.requireAcceptance(reserve, false, false, null))
                .isInstanceOf(BaseException.class);
        policy.requireAcceptance(reserve, true, false, null);
        var below = policy.scenarios(30, 40, 50, 15, 20).getFirst();
        assertThatThrownBy(() -> policy.requireAcceptance(below, false, true, " "))
                .isInstanceOf(BaseException.class);
        policy.requireAcceptance(below, false, true, "Chấp nhận nhóm tuyển sinh nhỏ");
        assertThatThrownBy(() -> policy.scenario(70, 1, 40, 50, 15, 20, true)).isInstanceOf(BaseException.class);
    }

    @ParameterizedTest
    @CsvSource({"0,50,15", "51,50,15", "40,50,-1", "40,90,10"})
    void rejectsInvalidCapacityConfiguration(int minimum, int maximum, int reserve) {
        assertThatThrownBy(() -> policy.validate(minimum, maximum, reserve)).isInstanceOf(BaseException.class);
    }

    @Test
    void everyScenarioPreservesPopulationAndDistributesRemainderAcrossFirstClasses() {
        for (int total = 1; total <= 400; total++) {
            for (var scenario : policy.scenarios(total, 20, 30, 5, 100)) {
                List<Integer> sizes = scenario.targetSizes();
                assertThat(sizes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(total);
                assertThat(sizes.getFirst() - sizes.getLast()).isBetween(0, 1);
                assertThat(sizes).isSortedAccordingTo(java.util.Comparator.reverseOrder());
                assertThat(sizes.getFirst()).isLessThanOrEqualTo(35);
            }
        }
        assertThat(policy.scenarios(Integer.MAX_VALUE, 1, 50, 15, 1)).isEmpty();
    }
}
