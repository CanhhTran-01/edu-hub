package com.eduhub.edu_hub.people.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.people.domain.model.Faculty;
import com.eduhub.edu_hub.people.infrastructure.persistence.LecturerCodeCounterRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.response.SystemConfigSnapshotResponse;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

class LecturerCodeGeneratorTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SystemConfigService systemConfigService = SystemConfigTestData.configuredService();
    private final LecturerCodeGenerator lecturerCodeGenerator =
            new LecturerCodeGenerator(new LecturerCodeCounterRepository(jdbcTemplate), systemConfigService);

    @Test
    void formatsScopedSequencesAndStopsAtNineHundredNinetyNineWithoutRetry() {
        Faculty faculty = faculty(1L, "CNTT");
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(1L), eq(2026), eq(999)))
                .thenReturn(List.of(1))
                .thenReturn(List.of(2))
                .thenReturn(List.of(999))
                .thenReturn(List.of());

        assertThat(allocateAndGenerateLecturerCode(faculty, 2026)).isEqualTo("CNTT26001");
        assertThat(allocateAndGenerateLecturerCode(faculty, 2026)).isEqualTo("CNTT26002");
        assertThat(allocateAndGenerateLecturerCode(faculty, 2026)).isEqualTo("CNTT26999");
        assertThatThrownBy(() -> allocateAndGenerateLecturerCode(faculty, 2026))
                .isInstanceOfSatisfying(
                        ProfileCreationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LECTURER_CODE_EXHAUSTED));

        verify(jdbcTemplate, times(4)).queryForList(anyString(), eq(Integer.class), eq(1L), eq(2026), eq(999));
    }

    @Test
    void usesIndependentFacultyAndYearScopes() {
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(1L), eq(2026), eq(999)))
                .thenReturn(List.of(1));
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(2L), eq(2026), eq(999)))
                .thenReturn(List.of(1));
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(1L), eq(2027), eq(999)))
                .thenReturn(List.of(1));
        assertThat(allocateAndGenerateLecturerCode(faculty(1L, "CNTT"), 2026)).isEqualTo("CNTT26001");
        assertThat(allocateAndGenerateLecturerCode(faculty(2L, "ATTT"), 2026)).isEqualTo("ATTT26001");
        assertThat(allocateAndGenerateLecturerCode(faculty(1L, "CNTT"), 2027)).isEqualTo("CNTT27001");
    }

    @Test
    void refusesUnknownFacultyCodeBeforeTouchingCounter() {
        assertThatThrownBy(() -> allocateAndGenerateLecturerCode(faculty(1L, null), 2026))
                .isInstanceOfSatisfying(
                        ProfileCreationException.class, exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_LECTURER_CODE_FACULTY));
        assertThatThrownBy(() -> allocateAndGenerateLecturerCode(faculty(1L, "cntt"), 2026))
                .isInstanceOf(ProfileCreationException.class);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void requiresFullRecruitmentYearInsteadOfGuessingCurrentYear() {
        for (Integer recruitmentYear : new Integer[] {null, 26, 10000}) {
            assertThatThrownBy(() -> allocateAndGenerateLecturerCode(faculty(1L, "CNTT"), recruitmentYear))
                    .isInstanceOfSatisfying(
                            ProfileCreationException.class,
                            exception -> assertThat(exception.getField()).isEqualTo("recruitmentYear"));
        }
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void generatesCodeFromProvidedSequenceWithoutAllocatingAnotherNumber() {
        Faculty faculty = faculty(1L, "CNTT");
        assertThat(lecturerCodeGenerator.generateLecturerCode(faculty, 2026, 1)).isEqualTo("CNTT26001");
        assertThat(lecturerCodeGenerator.generateLecturerCode(faculty, 2026, 999))
                .isEqualTo("CNTT26999");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsInvalidProvidedSequenceWithoutTouchingTheCounter() {
        Faculty faculty = faculty(1L, "CNTT");
        for (int lecturerSequence : new int[] {0, 1000}) {
            assertThatThrownBy(() -> lecturerCodeGenerator.generateLecturerCode(faculty, 2026, lecturerSequence))
                    .isInstanceOfSatisfying(
                            ProfileCreationException.class, exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.LECTURER_CODE_EXHAUSTED));
        }
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void resolvesYamlFormatPropertyUsedByTheGeneratorWithoutStartingApplicationContext() throws Exception {
        YamlPropertiesFactoryBean yamlPropertiesFactory = new YamlPropertiesFactoryBean();
        yamlPropertiesFactory.setResources(new ClassPathResource("application.yaml"));
        var yamlProperties = java.util.Objects.requireNonNull(yamlPropertiesFactory.getObject());
        assertThat(yamlProperties.getProperty("people.lecturer-code-format"))
                .isEqualTo("[MaKhoa][NamTuyenDung][SttGV]");
        yamlProperties.setProperty("people.lecturer-code-format", "[MaKhoa]-[NamTuyenDung]-[SttGV]");
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addLast(new PropertiesPropertySource("applicationYaml", yamlProperties));
        String formatExpression = "${people.lecturer-code-format}";
        String resolvedFormat =
                new PropertySourcesPropertyResolver(propertySources).resolveRequiredPlaceholders(formatExpression);
        configureFormat(resolvedFormat);

        assertThat(lecturerCodeGenerator.generateLecturerCode(faculty(1L, "CNTT"), 2026, 1))
                .isEqualTo("CNTT-26-001");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void supportsConfiguredComponentOrderAndPreservesYearAndSequencePadding() {
        configureFormat("[SttGV]_[MaKhoa]-[NamTuyenDung]");
        assertThat(lecturerCodeGenerator.generateLecturerCode(faculty(1L, "CNTT"), 2006, 7))
                .isEqualTo("007_CNTT-06");
        verifyNoInteractions(jdbcTemplate);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "[NamTuyenDung][SttGV]",
                "[MaKhoa][SttGV]",
                "[MaKhoa][NamTuyenDung]",
                "[MaKhoa][MaKhoa][NamTuyenDung][SttGV]",
                "[MaKhoa][NamTuyenDung][NamTuyenDung][SttGV]",
                "[MaKhoa][NamTuyenDung][SttGV][SttGV]",
                "[MaKhoa][NamTuyenDung][SttGV][Unknown]",
                "[MaKhoa] [NamTuyenDung][SttGV]"
            })
    void rejectsInvalidFormulaBeforeAllocatingAnySequence(String codeFormat) {
        configureFormat(codeFormat);
        Faculty faculty = faculty(1L, "CNTT");
        assertThatThrownBy(() -> lecturerCodeGenerator.generateLecturerCode(faculty, 2026, 1))
                .isInstanceOfSatisfying(
                        ProfileCreationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> lecturerCodeGenerator.allocateNextLecturerSequence(faculty, 2026))
                .isInstanceOfSatisfying(
                        ProfileCreationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(jdbcTemplate);
    }

    private void configureFormat(String codeFormat) {
        var configuration = mock(SystemConfigSnapshotResponse.class);
        when(configuration.getString(SystemConfigKey.LECTURER_CODE_FORMAT)).thenReturn(codeFormat);
        when(systemConfigService.getSnapshot()).thenReturn(configuration);
    }

    private String allocateAndGenerateLecturerCode(Faculty faculty, Integer recruitmentYear) {
        int lecturerSequence = lecturerCodeGenerator.allocateNextLecturerSequence(faculty, recruitmentYear);
        return lecturerCodeGenerator.generateLecturerCode(faculty, recruitmentYear, lecturerSequence);
    }

    private Faculty faculty(Long facultyId, String facultyCode) {
        Faculty faculty = new Faculty();
        faculty.setId(facultyId);
        faculty.setFacultyCode(facultyCode);
        return faculty;
    }
}
