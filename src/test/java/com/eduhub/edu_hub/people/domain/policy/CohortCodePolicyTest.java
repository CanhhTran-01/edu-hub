package com.eduhub.edu_hub.people.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CohortCodePolicyTest {
    @ParameterizedTest
    @CsvSource({"1,K1", "8,K8", "20,K20", "99,K99"})
    void generatesUnpaddedCohortCodes(int sequence, String expectedCode) {
        assertThat(CohortCodePolicy.generateCode(sequence)).isEqualTo(expectedCode);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 100})
    void rejectsSequencesOutsideTheAgreedRange(int sequence) {
        assertThatThrownBy(() -> CohortCodePolicy.generateCode(sequence)).isInstanceOf(ProfileCreationException.class);
    }
}
