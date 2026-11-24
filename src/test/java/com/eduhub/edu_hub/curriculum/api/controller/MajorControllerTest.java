package com.eduhub.edu_hub.curriculum.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.curriculum.application.service.MajorQueryService;
import com.eduhub.edu_hub.curriculum.domain.enums.CatalogStatus;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.curriculum.domain.repository.MajorRepository;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MajorControllerTest {
    private final MajorRepository majorRepository = mock(MajorRepository.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MajorController(
                    new ApiResponseFactory(
                            Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), TimeConfig.APPLICATION_ZONE)),
                    new MajorQueryService(majorRepository)))
            .build();

    @Test
    void listsActiveAndInactiveMajorsWithoutExposingFacultyOrEntityMetadata() throws Exception {
        var inactiveMajor = Major.builder()
                .majorCode("ATTT")
                .majorName("An toàn thông tin")
                .status(CatalogStatus.INACTIVE)
                .build();
        inactiveMajor.setId(2L);
        var activeMajor = Major.builder()
                .majorCode("CNTT")
                .majorName("Công nghệ thông tin")
                .status(CatalogStatus.ACTIVE)
                .build();
        activeMajor.setId(1L);
        when(majorRepository.findAllByDeletedFalseOrderByMajorCodeAscIdAsc())
                .thenReturn(List.of(inactiveMajor, activeMajor));

        mvc.perform(get("/api/v1/majors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].majorCode").value("ATTT"))
                .andExpect(jsonPath("$.data[0].majorName").value("An toàn thông tin"))
                .andExpect(jsonPath("$.data[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data[1].majorCode").value("CNTT"))
                .andExpect(jsonPath("$.data[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].faculty").doesNotExist())
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist());
        verify(majorRepository).findAllByDeletedFalseOrderByMajorCodeAscIdAsc();
        verifyNoMoreInteractions(majorRepository);
    }

    @Test
    void returnsAnEmptyListWhenNoUndeletedMajorsExist() throws Exception {
        when(majorRepository.findAllByDeletedFalseOrderByMajorCodeAscIdAsc()).thenReturn(List.of());

        mvc.perform(get("/api/v1/majors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
