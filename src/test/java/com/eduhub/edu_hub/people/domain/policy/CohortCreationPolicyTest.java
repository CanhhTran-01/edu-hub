package com.eduhub.edu_hub.people.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.people.application.exception.CohortException;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CohortCreationPolicyTest {
    @ParameterizedTest
    @CsvSource({"1,K1", "8,K8", "20,K20", "99,K99"})
    void generatesUnpaddedCohortCodes(int sequence, String expectedCode) {
        assertThat(CohortCreationPolicy.generateCohortCode(sequence)).isEqualTo(expectedCode);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 100})
    void rejectsSequencesOutsideTheAgreedRange(int sequence) {
        assertThatThrownBy(() -> CohortCreationPolicy.generateCohortCode(sequence))
                .isInstanceOf(ProfileCreationException.class);
    }

    @ParameterizedTest
    @CsvSource({"2026,2030", "2027,2031", "1,2", "9998,9999", "2026,2027"})
    void acceptsValidYearsWithoutImposingAFixedStudyDuration(int startYear, int endYear) {
        assertThatCode(() -> CohortCreationPolicy.validateYears(startYear, endYear))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
        "0,2030,startYear",
        "9999,10000,startYear",
        "2026,1,endYear",
        "2026,10000,endYear",
        "2026,2026,endYear",
        "2026,2025,endYear"
    })
    void rejectsYearsOutsideTheStorageRangeOrReversedYears(int startYear, int endYear, String expectedField) {
        assertThatThrownBy(() -> CohortCreationPolicy.validateYears(startYear, endYear))
                .isInstanceOfSatisfying(CohortException.class, exception -> assertThat(exception.getField())
                        .isEqualTo(expectedField));
    }

    @Test
    void requiresBothYearsForNewCohorts() {
        assertThatThrownBy(() -> CohortCreationPolicy.validateYears(null, 2030)).isInstanceOf(CohortException.class);
        assertThatThrownBy(() -> CohortCreationPolicy.validateYears(2026, null)).isInstanceOf(CohortException.class);
    }
}
