package com.eduhub.edu_hub.shared.systemconfig.domain.policy;

import static com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.*;
import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CodePrefixMappingPolicyTest {
    @ParameterizedTest
    @CsvSource({"Công nghệ thông tin,CNTT", " An   toàn thông tin ,ATTT", "Địa lý,DL", "Luật,L"})
    void derivesLookupKeyFromAllWordInitialsWithoutDiacritics(String name, String expectedKey) {
        assertThat(CodePrefixMappingPolicy.lookupKeyFromName(name)).isEqualTo(expectedKey);
        assertThat(CodePrefixMappingPolicy.lookupKeyFromName(
                        java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)))
                .isEqualTo(expectedKey);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "123--"})
    void rejectsNamesWithoutLetters(String name) {
        assertThatThrownBy(() -> CodePrefixMappingPolicy.lookupKeyFromName(name))
                .isInstanceOf(SystemConfigException.class);
    }

    @Test
    void normalizesMappingsAndSupportsTrainingTypeEnumKeys() {
        assertThat(CodePrefixMappingPolicy.normalizeMappings(
                        "{\" cntt \":\" ct \",\"attt\":\"at\"}", MAJOR_CODE_PREFIXES))
                .isEqualTo("{\"ATTT\":\"AT\",\"CNTT\":\"CT\"}");
        assertThat(CodePrefixMappingPolicy.parseMappings("{\"VAN_BANG_2\":\"VB2\"}", TRAINING_TYPE_CODE_PREFIXES))
                .containsEntry("VAN_BANG_2", "VB2");
        assertThat(CodePrefixMappingPolicy.parseMappings("{}", MAJOR_CODE_PREFIXES))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "null",
                "{} {}",
                "{",
                "{\"CNTT\":null}",
                "{\"CNTT\":1}",
                "{\"CNTT\":{}}",
                "{\"CNTT\":\"CT1\"}",
                "{\"CNTT\":\"C T\"}",
                "{\"CNTT\":\"CT\",\"CNTT\":\"IT\"}",
                "{\"CNTT\":\"CT\",\"cntt\":\"IT\"}",
                "{\"CNTT\":\"CT\",\"ATTT\":\"ct\"}",
                "{\"A_\":\"CT\"}"
            })
    void rejectsMalformedAmbiguousOrNonTextMappings(String json) {
        assertThatThrownBy(() -> CodePrefixMappingPolicy.parseMappings(json, MAJOR_CODE_PREFIXES))
                .isInstanceOfSatisfying(SystemConfigException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void missingMappingExplainsTheExactConfigurationAndDoesNotInferAPrefix() {
        assertThatThrownBy(() -> CodePrefixMappingPolicy.requirePrefix(Map.of(), MAJOR_CODE_PREFIXES, "CNTT"))
                .isInstanceOfSatisfying(SystemConfigException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CODE_PREFIX_MAPPING_MISSING);
                    assertThat(exception.getFieldErrors()).singleElement().satisfies(error -> {
                        assertThat(error.getField()).isEqualTo("people.major-code-prefixes");
                        assertThat(error.getMessage())
                                .contains("CNTT", "SYSTEM_ADMIN", "Cấu hình hệ thống", "lưu cấu hình");
                    });
                });
    }

    @Test
    void detectsDifferentCatalogNamesWithTheSameLookupKey() {
        assertThatThrownBy(() -> CodePrefixMappingPolicy.requireUnambiguousName(
                        "Công nghệ thông tin",
                        List.of("Công nghệ thông tin", "Công nghệ truyền thông"),
                        MAJOR_CODE_PREFIXES))
                .isInstanceOfSatisfying(SystemConfigException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CODE_PREFIX_MAPPING_AMBIGUOUS));
    }
}
