package com.eduhub.edu_hub.scheduling.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.scheduling.api.controller.RoomController;
import com.eduhub.edu_hub.scheduling.api.dto.*;
import com.eduhub.edu_hub.scheduling.api.exception.RoomExceptionHandler;
import com.eduhub.edu_hub.scheduling.application.RoomApplicationService;
import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.sql.SQLException;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {
    private static final String BODY =
            """
            {"roomCode":"102-TA1","roomName":"Phòng 102","buildingCode":"TA1","roomNumber":"102","capacity":80}
            """;

    @Mock
    private RoomApplicationService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RoomController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new RoomExceptionHandler())
                .build();
    }

    @Test
    void createsRoomAndReturns201WithDtoEnvelope() throws Exception {
        when(service.create(any(CreateRoomRequest.class))).thenReturn(room());

        mvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roomCode").value("102-TA1"))
                .andExpect(jsonPath("$.data.capacity").value(80))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roomType").doesNotExist())
                .andExpect(jsonPath("$.data.deleted").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-1", "80.5", "80.0", "\"80\"", "true", "2147483648"})
    void rejectsInvalidCapacityOnCreateAndUpdate(String capacity) throws Exception {
        String invalid = BODY.replace("\"capacity\":80", "\"capacity\":" + capacity);
        mvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        mvc.perform(put("/api/v1/rooms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"roomCode", "roomName", "buildingCode", "capacity"})
    void missingRequiredCreateFieldReturns400(String field) throws Exception {
        String invalid = BODY.replaceAll("\"" + field + "\":(\"[^\"]*\"|[0-9]+)", "\"" + field + "\":null");
        mvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(service);
    }

    @Test
    void blankAndOversizedIdentifiersAreRejected() throws Exception {
        for (String value : new String[] {" ", "A".repeat(51)}) {
            mvc.perform(post("/api/v1/rooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY.replace("102-TA1", value)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(1001));
        }
        verifyNoInteractions(service);
    }

    @Test
    void searchBindsAllFiltersAndReturnsEmptyPage() throws Exception {
        when(service.search(any(RoomSearchRequest.class)))
                .thenReturn(PageResponse.<RoomResponse>builder()
                        .content(List.of())
                        .pageNumber(0)
                        .pageSize(20)
                        .totalElements(0)
                        .totalPages(0)
                        .isLast(true)
                        .build());

        mvc.perform(get("/api/v1/rooms")
                        .param("keyword", "102")
                        .param("buildingCode", "ta1")
                        .param("minCapacity", "60")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Không tìm thấy kết quả"))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.last").value(true));

        var captor = ArgumentCaptor.forClass(RoomSearchRequest.class);
        verify(service).search(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue()).satisfies(request -> {
            org.assertj.core.api.Assertions.assertThat(request.getKeyword()).isEqualTo("102");
            org.assertj.core.api.Assertions.assertThat(request.getBuildingCode())
                    .isEqualTo("ta1");
            org.assertj.core.api.Assertions.assertThat(request.getMinCapacity()).isEqualTo(60);
            org.assertj.core.api.Assertions.assertThat(request.getStatus()).isEqualTo(RoomStatus.ACTIVE);
        });
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "minCapacity,0", "minCapacity,abc", "status,UNKNOWN"})
    void invalidSearchFiltersReturn400(String field, String value) throws Exception {
        mvc.perform(get("/api/v1/rooms").param(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @Test
    void returnsDetailsOr404UsingBusinessErrorCode() throws Exception {
        when(service.findById(1L)).thenReturn(room());
        when(service.findById(99L)).thenThrow(new BaseException(ErrorCode.ROOM_NOT_FOUND));

        mvc.perform(get("/api/v1/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomNumber").value("102"));
        mvc.perform(get("/api/v1/rooms/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4326));
    }

    @Test
    void updatesRoomWithoutReason() throws Exception {
        when(service.update(eq(1L), any(UpdateRoomRequest.class))).thenReturn(room());

        mvc.perform(put("/api/v1/rooms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void changesStatusWithoutReason() throws Exception {
        var room = room();
        room.setStatus(RoomStatus.INACTIVE);
        when(service.changeStatus(eq(1L), any(ChangeRoomStatusRequest.class))).thenReturn(room);

        mvc.perform(patch("/api/v1/rooms/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"UNKNOWN\"}"})
    void rejectsMissingOrInvalidStatus(String body) throws Exception {
        mvc.perform(patch("/api/v1/rooms/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @Test
    void deletesWithoutBodyAndReturnsSuccessWithoutData() throws Exception {
        mvc.perform(delete("/api/v1/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(service).delete(eq(1L));
    }

    @ParameterizedTest
    @CsvSource({"uk_rooms_code,4327", "uk_rooms_location,4328"})
    void concurrentUniqueConstraintViolationsReturn409(String constraint, int code) throws Exception {
        when(service.create(any(CreateRoomRequest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Could not insert",
                        new ConstraintViolationException("Duplicate", new SQLException("rejected"), constraint)));

        mvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.path").value("/api/v1/rooms"));
    }

    @Test
    void unknownDatabaseFailureDoesNotLeakSql() throws Exception {
        when(service.create(any(CreateRoomRequest.class)))
                .thenThrow(new DataIntegrityViolationException("SQL details must not be returned"));

        mvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("Lỗi hệ thống"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TA1-102", "CS1-102-TA1", "102TA1", "102_TA1", "102--TA1", "102-TA1!", "102A-TA1"})
    void invalidRoomCodeReturnsSpecificFieldError(String code) throws Exception {
        mvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("102-TA1", code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("roomCode"));
        mvc.perform(put("/api/v1/rooms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("102-TA1", code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("roomCode"));
        verifyNoInteractions(service);
    }

    @Test
    void a2AndA3ReturnAffectedClassesInErrors() throws Exception {
        var errors = List.of(new com.eduhub.edu_hub.shared.common.FieldErrorDetail(
                "moduleClasses[20]", "MC_TEST_20; sức chứa tối đa: 60"));
        when(service.changeStatus(eq(1L), any(ChangeRoomStatusRequest.class)))
                .thenThrow(new com.eduhub.edu_hub.scheduling.application.exception.RoomConflictException(
                        ErrorCode.ROOM_IN_USE, errors));
        mvc.perform(patch("/api/v1/rooms/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4330))
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("MC_TEST_20")));
        when(service.update(eq(1L), any(UpdateRoomRequest.class)))
                .thenThrow(new com.eduhub.edu_hub.scheduling.application.exception.RoomConflictException(
                        ErrorCode.ROOM_CAPACITY_BELOW_CLASS, errors));
        mvc.perform(put("/api/v1/rooms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4331))
                .andExpect(jsonPath("$.errors[0].field").value("moduleClasses[20]"));
    }

    private RoomResponse room() {
        return RoomResponse.builder()
                .id(1L)
                .roomCode("102-TA1")
                .roomName("Phòng 102")
                .campus("")
                .buildingCode("TA1")
                .roomNumber("102")
                .capacity(80)
                .status(RoomStatus.ACTIVE)
                .build();
    }
}
