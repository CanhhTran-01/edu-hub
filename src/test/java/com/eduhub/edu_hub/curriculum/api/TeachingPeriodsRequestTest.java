package com.eduhub.edu_hub.curriculum.api;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.curriculum.api.dto.request.CreateSubjectRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.UpdateSubjectRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class TeachingPeriodsRequestTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"45.0", "45.5", "\"45\"", "true"})
    void rejectsCoercionBeforeValidation(String token) {
        assertThatThrownBy(
                        () -> mapper.readValue("{\"totalTeachingPeriods\":" + token + "}", UpdateSubjectRequest.class))
                .hasMessageContaining("Tổng số tiết phải là số nguyên");
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
        assertThat(mapper.readValue("{\"totalTeachingPeriods\":46}", UpdateSubjectRequest.class)
                        .getTotalTeachingPeriods())
                .isEqualTo(46);
    }
}
