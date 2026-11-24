package com.eduhub.edu_hub.shared.systemconfig.application.service;

import static com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.request.UpdateSystemConfigsRequest;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.request.UpdateSystemConfigsRequest.ConfigurationUpdateRequest;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import com.eduhub.edu_hub.shared.systemconfig.domain.model.SystemConfig;
import com.eduhub.edu_hub.shared.systemconfig.domain.policy.SystemConfigValidationPolicy;
import com.eduhub.edu_hub.shared.systemconfig.domain.repository.SystemConfigRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

class SystemConfigServiceTest {
    private static ValidatorFactory validatorFactory;
    private final SystemConfigRepository repository = mock(SystemConfigRepository.class);
    private final MockEnvironment environment = new MockEnvironment();
    private List<SystemConfig> configurations;
    private SystemConfigService service;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        configurations = Arrays.stream(SystemConfigKey.values())
                .map(key -> SystemConfig.builder().configKey(key.propertyName()).build())
                .toList();
        when(repository.findByDeletedFalseOrderByConfigKeyAsc()).thenReturn(configurations);
        when(repository.findConfigurationsForUpdate()).thenReturn(configurations);
        service = new SystemConfigService(
                repository, environment, new SystemConfigValidationPolicy(validatorFactory.getValidator()));
    }

    @Test
    void usesDeploymentValuesUntilAdministratorConfiguresOverrideAndReadsFreshSnapshots() {
        environment
                .withProperty("admission.min-capacity", "45")
                .withProperty("jwt.access-token-lifetime", "5m")
                .withProperty("admission.notifications-enabled", "false");
        var original = service.getSnapshot();
        assertThat(original.getInteger(ADMISSION_MINIMUM_CAPACITY)).isEqualTo(45);
        assertThat(original.getDuration(ACCESS_TOKEN_LIFETIME)).isEqualTo(Duration.ofMinutes(5));
        assertThat(original.getBoolean(ADMISSION_NOTIFICATIONS_ENABLED)).isFalse();

        service.updateConfigurations(request(change(ADMISSION_MINIMUM_CAPACITY, "42", 0)), 7L);
        environment.withProperty("admission.min-capacity", "48");
        assertThat(service.getSnapshot().getInteger(ADMISSION_MINIMUM_CAPACITY)).isEqualTo(42);
        assertThat(original.getInteger(ADMISSION_MINIMUM_CAPACITY)).isEqualTo(45);
        assertThat(configuration(ADMISSION_MINIMUM_CAPACITY).getUpdatedBy()).isEqualTo("7");
    }

    @Test
    void doesNotInventCreditRegulationsAndAllowsAdministratorToConfigureBothTogether() {
        environment
                .withProperty("enrollment.registration.minimum-credits", "")
                .withProperty("enrollment.registration.maximum-credits", "");
        assertThat(service.findConfigurations(Group.ENROLLMENT))
                .allMatch(configuration -> configuration.value() == null);
        assertThatThrownBy(() -> service.getSnapshot().getInteger(ENROLLMENT_MAXIMUM_CREDITS))
                .isInstanceOfSatisfying(SystemConfigException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SYSTEM_CONFIG_UNAVAILABLE));
        service.updateConfigurations(
                request(change(ENROLLMENT_MINIMUM_CREDITS, "12", 0), change(ENROLLMENT_MAXIMUM_CREDITS, "24", 0)), 7L);
        assertThat(service.getSnapshot().getInteger(ENROLLMENT_MAXIMUM_CREDITS)).isEqualTo(24);
    }

    @Test
    void validatesWholeBatchBeforeMutatingAnyManagedEntity() {
        assertThatThrownBy(() -> service.updateConfigurations(
                        request(
                                change(ADMISSION_MINIMUM_CAPACITY, "60", 0),
                                change(ADMISSION_MAXIMUM_CAPACITY, "55", 0)),
                        7L))
                .isInstanceOfSatisfying(SystemConfigException.class, exception -> assertThat(exception.getFieldErrors())
                        .extracting("field")
                        .contains(ADMISSION_MINIMUM_CAPACITY.propertyName()));
        assertThat(configurations).allMatch(configuration -> configuration.getConfigValue() == null);
        verify(repository, never()).flush();

        service.updateConfigurations(
                request(change(ADMISSION_MINIMUM_CAPACITY, "60", 0), change(ADMISSION_MAXIMUM_CAPACITY, "65", 0)), 7L);
        assertThat(service.getSnapshot().getInteger(ADMISSION_MINIMUM_CAPACITY)).isEqualTo(60);
        verify(repository).flush();
    }

    @Test
    void refusesStaleRevisionWithoutPartiallyApplyingEarlierChanges() {
        configuration(ADMISSION_MAXIMUM_CAPACITY).setRevision(2);
        assertThatThrownBy(() -> service.updateConfigurations(
                        request(
                                change(ADMISSION_MINIMUM_CAPACITY, "42", 0),
                                change(ADMISSION_MAXIMUM_CAPACITY, "55", 1)),
                        7L))
                .isInstanceOfSatisfying(SystemConfigException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SYSTEM_CONFIG_CHANGED));
        assertThat(configurations).allMatch(configuration -> configuration.getConfigValue() == null);
        verify(repository, never()).flush();
    }

    @Test
    void rejectsDuplicateKeysAndMissingCatalogRows() {
        assertThatThrownBy(() -> service.updateConfigurations(
                        request(change(MAIL_BRAND_NAME, "Tên mới", 0), change(MAIL_BRAND_NAME, "Tên khác", 0)), 7L))
                .isInstanceOf(SystemConfigException.class);
        when(repository.findByDeletedFalseOrderByConfigKeyAsc()).thenReturn(List.of());
        assertThatThrownBy(service::getSnapshot)
                .isInstanceOfSatisfying(SystemConfigException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SYSTEM_CONFIG_UNAVAILABLE));
        verify(repository, never()).flush();
    }

    @ParameterizedTest
    @CsvSource({
        "admission.min-capacity,0",
        "admission.reserve-capacity,60",
        "identity.password.minimum-length,7",
        "identity.password.minimum-length,73",
        "identity.authentication.max-failed-logins,0",
        "jwt.access-token-lifetime,0s",
        "jwt.refresh-token-lifetime,-1d",
        "notification.admission-enabled,yes",
        "notification.mail.login-url,ftp://example.com",
        "notification.mail.support-email,invalid",
        "scheduling.module-class.default-capacity,100",
        "enrollment.registration.maximum-credits,20"
    })
    void rejectsInvalidTypesBoundsAndIncompleteDependentGroups(String propertyName, String proposedValue) {
        assertThatThrownBy(() -> service.updateConfigurations(
                        request(change(SystemConfigKey.fromPropertyName(propertyName), proposedValue, 0)), 7L))
                .isInstanceOfSatisfying(SystemConfigException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(configurations).allMatch(configuration -> configuration.getConfigValue() == null);
        verify(repository, never()).flush();
    }

    @Test
    void validatesIdentityTokensBeforePersistingCodeFormats() {
        assertThatThrownBy(() -> service.updateConfigurations(
                        request(change(STUDENT_CODE_FORMAT, "[MaNganh][SttSVTrongLop]", 0)), 7L))
                .isInstanceOf(SystemConfigException.class);
        assertThatThrownBy(() -> service.updateConfigurations(
                        request(change(LECTURER_CODE_FORMAT, "[MaKhoa][MaKhoa][NamTuyenDung][SttGV]", 0)), 7L))
                .isInstanceOf(SystemConfigException.class);
        service.updateConfigurations(
                request(change(STUDENT_CODE_FORMAT, "[MaNganh]-[SttKhoa]-[SttLop]-[SttSVTrongLop]", 0)), 7L);
        assertThat(service.getSnapshot().getString(STUDENT_CODE_FORMAT)).contains("-");
    }

    @Test
    void persistsExplicitEmptyOptionalMailValuesAndDoesNotOverwriteUnchangedActor() {
        environment.withProperty("notification.mail.login-url", "https://example.com");
        service.updateConfigurations(request(change(MAIL_LOGIN_URL, "", 0)), 7L);
        assertThat(service.getSnapshot().getString(MAIL_LOGIN_URL)).isEmpty();
        service.updateConfigurations(request(change(MAIL_LOGIN_URL, "", 0)), 8L);
        assertThat(configuration(MAIL_LOGIN_URL).getUpdatedBy()).isEqualTo("7");
        assertThat(service.findConfigurations(Group.NOTIFICATION))
                .filteredOn(configuration -> configuration.key() == MAIL_LOGIN_URL)
                .allMatch(configuration ->
                        configuration.configured() && configuration.value().isEmpty());
    }

    @Test
    void acceptsLargerModuleClassCapacityOnlyWhenDefaultAndMaximumRemainConsistent() {
        service.updateConfigurations(
                request(
                        change(MODULE_CLASS_DEFAULT_CAPACITY, "100", 0),
                        change(MODULE_CLASS_MAXIMUM_CAPACITY, "120", 0)),
                7L);
        assertThat(service.getSnapshot().getInteger(MODULE_CLASS_MAXIMUM_CAPACITY))
                .isEqualTo(120);
    }

    @Test
    void keepsMissingMappingsLocalToCodeIssuanceAndAcceptsAdministratorUpdates() {
        var empty = service.getSnapshot();
        assertThat(empty.getCodePrefixes(MAJOR_CODE_PREFIXES)).isEmpty();
        assertThat(empty.getInteger(ADMISSION_MAXIMUM_CAPACITY)).isEqualTo(50);
        assertThatThrownBy(() -> empty.requireNameCodePrefix(MAJOR_CODE_PREFIXES, "Công nghệ thông tin"))
                .isInstanceOfSatisfying(SystemConfigException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CODE_PREFIX_MAPPING_MISSING));
        service.updateConfigurations(
                request(
                        change(MAJOR_CODE_PREFIXES, "{\"cntt\":\"ct\"}", 0),
                        change(TRAINING_TYPE_CODE_PREFIXES, "{\"VAN_BANG_2\":\"VB2\"}", 0)),
                7L);
        assertThat(service.getSnapshot().requireNameCodePrefix(MAJOR_CODE_PREFIXES, "Công nghệ thông tin"))
                .isEqualTo("CT");
        assertThat(empty.getCodePrefixes(MAJOR_CODE_PREFIXES)).isEmpty();
        assertThat(configuration(MAJOR_CODE_PREFIXES).getConfigValue()).isEqualTo("{\"CNTT\":\"CT\"}");
        assertThat(service.findConfigurations(Group.CURRICULUM))
                .singleElement()
                .satisfies(configuration -> assertThat(configuration.dataType()).isEqualTo(DataType.STRING_MAP));
    }

    @Test
    void rejectsInvalidMappingsBeforeChangingAnyConfiguration() {
        assertThatThrownBy(() -> service.updateConfigurations(
                        request(
                                change(MAIL_BRAND_NAME, "Tên mới", 0),
                                change(MAJOR_CODE_PREFIXES, "{\"CNTT\":\"CT\",\"ATTT\":\"CT\"}", 0)),
                        7L))
                .isInstanceOf(SystemConfigException.class);
        assertThat(configurations).allMatch(configuration -> configuration.getConfigValue() == null);
        verify(repository, never()).flush();
    }

    private SystemConfig configuration(SystemConfigKey key) {
        return configurations.stream()
                .filter(configuration -> configuration.getConfigKey().equals(key.propertyName()))
                .findFirst()
                .orElseThrow();
    }

    private ConfigurationUpdateRequest change(SystemConfigKey key, String proposedValue, long revision) {
        return new ConfigurationUpdateRequest(key, proposedValue, revision);
    }

    private UpdateSystemConfigsRequest request(ConfigurationUpdateRequest... changes) {
        return new UpdateSystemConfigsRequest(List.of(changes));
    }
}
