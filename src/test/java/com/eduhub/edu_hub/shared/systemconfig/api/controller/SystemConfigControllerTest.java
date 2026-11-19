package com.eduhub.edu_hub.shared.systemconfig.api.controller;

import static com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.request.UpdateSystemConfigsRequest;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.response.SystemConfigResponse;
import com.eduhub.edu_hub.shared.systemconfig.api.exception.SystemConfigExceptionHandler;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigAdministrationService;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SystemConfigControllerTest {
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final SystemConfigService service = mock(SystemConfigService.class);
    private final Jwt principal = Jwt.withTokenValue("unit-token")
            .header("alg", "HS256")
            .subject("admin")
            .claim("token_use", "ACCESS")
            .build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var responses = new ApiResponseFactory(Clock.systemUTC());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(principal));
        when(identityFacade.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder()
                        .id(7L)
                        .role("SYSTEM_ADMIN")
                        .status("ACTIVE")
                        .build());
        mvc = MockMvcBuilders.standaloneSetup(new SystemConfigController(
                        new SystemConfigAdministrationService(identityFacade, service), responses))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new SystemConfigExceptionHandler(responses), new GlobalExceptionHandler(responses))
                .build();
    }

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsGroupWithPublicPropertyNamesAndPreventsCaching() throws Exception {
        when(service.findConfigurations(Group.IDENTITY))
                .thenReturn(List.of(new SystemConfigResponse(
                        ACCESS_TOKEN_LIFETIME,
                        Group.IDENTITY,
                        DataType.DURATION,
                        "Thời hạn access token",
                        "PT15M",
                        false,
                        0,
                        null,
                        null)));
        mvc.perform(get("/api/v1/admin/system-configs").param("group", "IDENTITY"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data[0].key").value("jwt.access-token-lifetime"))
                .andExpect(jsonPath("$.data[0].value").value("PT15M"))
                .andExpect(jsonPath("$.data[0].configured").value(false));
    }

    @Test
    void passesBatchAndRevisionsWithActorTakenFromAuthenticatedAccount() throws Exception {
        when(service.updateConfigurations(any(), eq(7L))).thenReturn(List.of());
        mvc.perform(
                        patch("/api/v1/admin/system-configs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"configurations":[
                      {"key":"admission.min-capacity","value":"45","expectedRevision":2},
                      {"key":"admission.max-capacity","value":"55","expectedRevision":3}
                    ]}
                    """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        var request = ArgumentCaptor.forClass(UpdateSystemConfigsRequest.class);
        verify(service).updateConfigurations(request.capture(), eq(7L));
        assertThat(request.getValue().configurations())
                .extracting("expectedRevision")
                .containsExactly(2L, 3L);
        assertThat(request.getValue().configurations())
                .extracting("key")
                .containsExactly(ADMISSION_MINIMUM_CAPACITY, ADMISSION_MAXIMUM_CAPACITY);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"configurations\":[]}",
                "{\"configurations\":[null]}",
                "{\"configurations\":[{\"key\":\"jwt.secret\",\"value\":\"secret\",\"expectedRevision\":0}]}",
                "{\"configurations\":[{\"key\":\"admission.min-capacity\",\"value\":null,\"expectedRevision\":0}]}",
                "{\"configurations\":[{\"key\":\"admission.min-capacity\",\"value\":\"45\"}]}",
                "{\"configurations\":[{\"key\":\"admission.min-capacity\",\"value\":\"45\",\"expectedRevision\":-1}]}",
                "{\"configurations\":[{\"key\":\"admission.min-capacity\",\"value\":\"45\",\"expectedRevision\":0.5}]}",
                "{\"configurations\":[{\"key\":\"admission.min-capacity\",\"value\":\"45\",\"expectedRevision\":\"0\"}]}",
                "{\"configurations\":[{\"key\":\"admission.min-capacity\",\"value\":\"45\",\"expectedRevision\":true}]}"
            })
    void rejectsIncompleteBodiesAndUnsupportedKeysBeforeCallingService(String body) throws Exception {
        mvc.perform(patch("/api/v1/admin/system-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @Test
    void returnsStaleRevisionAndFieldErrorsThroughSpecificHandler() throws Exception {
        when(service.updateConfigurations(any(), eq(7L)))
                .thenThrow(new SystemConfigException(
                        ErrorCode.SYSTEM_CONFIG_CHANGED,
                        List.of(new FieldErrorDetail("admission.min-capacity", "Hãy tải lại"))));
        mvc.perform(
                        patch("/api/v1/admin/system-configs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"configurations":[{"key":"admission.min-capacity","value":"45","expectedRevision":0}]}
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4701))
                .andExpect(jsonPath("$.errors[0].field").value("admission.min-capacity"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void deniesNonAdministratorAndMissingPrincipalAtServiceBoundary() throws Exception {
        when(identityFacade.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder()
                        .id(7L)
                        .role("STUDENT")
                        .status("ACTIVE")
                        .build());
        mvc.perform(get("/api/v1/admin/system-configs")).andExpect(status().isForbidden());
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/admin/system-configs")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
