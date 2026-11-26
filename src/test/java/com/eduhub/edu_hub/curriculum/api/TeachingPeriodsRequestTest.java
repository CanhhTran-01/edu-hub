package com.eduhub.edu_hub.curriculum.api;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.curriculum.api.dto.request.CreateSubjectRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.UpdateSubjectRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class TeachingPeriodsRequestTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"45.0", "45.5", "\"45\""})
    void coercesNumericTeachingPeriodsForSubjectRequests(String teachingPeriodsToken) {
        String subjectRequestJson = "{\"totalTeachingPeriods\":" + teachingPeriodsToken + "}";
        assertThat(mapper.readValue(subjectRequestJson, CreateSubjectRequest.class)
                        .getTotalTeachingPeriods())
                .isEqualTo(45);
        assertThat(mapper.readValue(subjectRequestJson, UpdateSubjectRequest.class)
                        .getTotalTeachingPeriods())
                .isEqualTo(45);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "\"invalid\"", "2147483648"})
    void rejectsNonNumericOrOutOfRangeTeachingPeriods(String teachingPeriodsToken) {
        String subjectRequestJson = "{\"totalTeachingPeriods\":" + teachingPeriodsToken + "}";
        assertThatThrownBy(() -> mapper.readValue(subjectRequestJson, CreateSubjectRequest.class))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> mapper.readValue(subjectRequestJson, UpdateSubjectRequest.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void requiresPositiveTeachingPeriodsForNewSubjects() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new CreateSubjectRequest();
            request.setSubjectName("Môn học");
            request.setCredits(3);
            assertThat(factory.getValidator().validate(request))
                    .anyMatch(error -> error.getPropertyPath().toString().equals("totalTeachingPeriods"));
            request.setTotalTeachingPeriods(0);
            assertThat(factory.getValidator().validate(request))
                    .anyMatch(error -> error.getPropertyPath().toString().equals("totalTeachingPeriods"));
            request.setTotalTeachingPeriods(46);
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void distinguishesRequiredCreationFromOptionalLegacyUpdate() {
        var request = mapper.readValue("{\"subjectName\":\"Môn học\",\"credits\":3}", UpdateSubjectRequest.class);
        assertThat(request.getTotalTeachingPeriods()).isNull();
        assertThat(mapper.readValue("{\"totalTeachingPeriods\":null}", UpdateSubjectRequest.class)
                        .getTotalTeachingPeriods())
                .isNull();
        assertThat(mapper.readValue("{\"totalTeachingPeriods\":46}", UpdateSubjectRequest.class)
                        .getTotalTeachingPeriods())
                .isEqualTo(46);
    }
}
