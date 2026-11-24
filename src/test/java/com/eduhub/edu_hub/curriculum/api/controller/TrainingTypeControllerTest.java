package com.eduhub.edu_hub.curriculum.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.curriculum.application.service.TrainingTypeQueryService;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TrainingTypeControllerTest {
    @Test
    void listsEnumNamesAndConfiguredPrefixes() throws Exception {
        requestWithMapping("{\"CHINH_QUY\":\"CQ\",\"LIEN_THONG\":\"LT\",\"VAN_BANG_2\":\"VB2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].trainingType").value("CHINH_QUY"))
                .andExpect(jsonPath("$.data[0].label").value("Chính quy"))
                .andExpect(jsonPath("$.data[0].prefix").value("CQ"))
                .andExpect(jsonPath("$.data[0].hasPrefixMapping").value(true))
                .andExpect(jsonPath("$.data[2].prefix").value("VB2"));
    }

    @Test
    void missingMappingDoesNotPreventReadingTheDropdown() throws Exception {
        requestWithMapping("{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].hasPrefixMapping").value(false))
                .andExpect(jsonPath("$.data[0].prefix").isEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions requestWithMapping(String mapping) throws Exception {
        var configuration =
                SystemConfigTestData.configuredService(Map.of(SystemConfigKey.TRAINING_TYPE_CODE_PREFIXES, mapping));
        var controller = new TrainingTypeController(
                new ApiResponseFactory(Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), TimeConfig.APPLICATION_ZONE)),
                new TrainingTypeQueryService(configuration));
        return MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/v1/training-types"));
    }
}
