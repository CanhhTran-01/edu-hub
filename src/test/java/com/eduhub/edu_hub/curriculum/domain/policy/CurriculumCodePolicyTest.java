package com.eduhub.edu_hub.curriculum.domain.policy;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.curriculum.application.exception.CurriculumCodeException;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CurriculumCodePolicyTest {
    private static final Map<String, String> MAJORS = Map.of("CNTT", "CT", "ATTT", "AT");
    private static final Map<String, String> TRAINING_TYPES =
            Map.of("CHINH_QUY", "CQ", "LIEN_THONG", "LT", "VAN_BANG_2", "VB2");

    @Test
    void combinesConfiguredPrefixesAndCohortForEachTrainingType() {
        for (var trainingType : TrainingType.values())
            assertThat(CurriculumCodePolicy.generateCode(
                            "Công nghệ thông tin", "K8", trainingType, MAJORS, TRAINING_TYPES))
                    .isEqualTo("CTDT-CT-K8-" + TRAINING_TYPES.get(trainingType.name()));
        assertThat(CurriculumCodePolicy.generateCode(
                        "An toàn thông tin", "K20", TrainingType.CHINH_QUY, MAJORS, TRAINING_TYPES))
                .isEqualTo("CTDT-AT-K20-CQ");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"K0", "K08", "K100", "CT8", " K8"})
    void requiresCanonicalCohortCodes(String cohortCode) {
        assertThatThrownBy(() -> CurriculumCodePolicy.generateCode(
                        "Công nghệ thông tin", cohortCode, TrainingType.CHINH_QUY, MAJORS, TRAINING_TYPES))
                .isInstanceOf(CurriculumCodeException.class);
    }

    @Test
    void requiresBothMappingsAndAnExplicitTrainingType() {
        assertThatThrownBy(() -> CurriculumCodePolicy.generateCode(
                        "Công nghệ thông tin", "K8", TrainingType.CHINH_QUY, Map.of(), TRAINING_TYPES))
                .isInstanceOf(SystemConfigException.class);
        assertThatThrownBy(() -> CurriculumCodePolicy.generateCode(
                        "Công nghệ thông tin", "K8", TrainingType.CHINH_QUY, MAJORS, Map.of()))
                .isInstanceOf(SystemConfigException.class);
        assertThatThrownBy(() ->
                        CurriculumCodePolicy.generateCode("Công nghệ thông tin", "K8", null, MAJORS, TRAINING_TYPES))
                .isInstanceOf(CurriculumCodeException.class);
    }
}
