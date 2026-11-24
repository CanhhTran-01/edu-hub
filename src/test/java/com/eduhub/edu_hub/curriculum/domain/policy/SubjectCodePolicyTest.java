package com.eduhub.edu_hub.curriculum.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.curriculum.application.exception.SubjectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SubjectCodePolicyTest {
    @ParameterizedTest
    @CsvSource({"1,HP000001", "999999,HP999999", "1000000,HP1000000"})
    void padsToAtLeastSixDigitsWithoutTruncatingTheSequence(long sequence, String expectedCode) {
        assertThat(SubjectCodePolicy.generateCode(sequence)).isEqualTo(expectedCode);
    }

    @Test
    void rejectsMissingOrInvalidAllocatedSequences() {
        for (Long sequence : new Long[] {null, 0L, -1L})
            assertThatThrownBy(() -> SubjectCodePolicy.generateCode(sequence)).isInstanceOf(SubjectException.class);
    }
}
