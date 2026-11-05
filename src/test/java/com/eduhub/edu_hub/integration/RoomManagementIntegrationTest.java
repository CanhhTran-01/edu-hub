package com.eduhub.edu_hub.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.curriculum.domain.repository.SubjectRepository;
import com.eduhub.edu_hub.identity.api.AccessTokenFilter;
import com.eduhub.edu_hub.identity.api.dto.LoginRequest;
import com.eduhub.edu_hub.identity.application.AuthApplicationService;
import com.eduhub.edu_hub.scheduling.api.dto.*;
import com.eduhub.edu_hub.scheduling.application.RoomApplicationService;
import com.eduhub.edu_hub.scheduling.application.exception.RoomConflictException;
import com.eduhub.edu_hub.scheduling.application.specification.RoomScheduleSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.repository.*;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@Transactional
class RoomManagementIntegrationTest extends PostgresIntegrationTest {
    private static final AtomicInteger SEQUENCES = new AtomicInteger(100_000);

    @Autowired
    private RoomApplicationService service;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private ClassScheduleRepository schedules;

    @Autowired
    private ModuleClassRepository classes;

    @Autowired
    private SemesterRepository semesters;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private AuthApplicationService auth;

    @Autowired
    private AccessTokenFilter filter;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private String token;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        token = login("postman_academic_admin");
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
    }

    @Test
    void basicFlowPersistsRoomsWithoutAuditOrActorAssignment() throws Exception {
        var create = createRequest();
        create.setRoomCode(" 102-testta1 ");
        create.setBuildingCode(" testta1 ");
        String response = mvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomName").value("Phòng kiểm tra"))
                .andExpect(jsonPath("$.data.roomCode").value("102-TESTTA1"))
                .andExpect(jsonPath("$.data.roomNumber").value("102"))
                .andExpect(jsonPath("$.data.buildingCode").value("TESTTA1"))
                .andExpect(jsonPath("$.data.roomType").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = mapper.readTree(response).path("data").path("id").asLong();
        var persisted = rooms.findByIdAndDeletedFalse(id).orElseThrow();
        assertThat(persisted.getCreatedBy()).isNull();
        assertThat(persisted.getUpdatedBy()).isNull();
        var update = updateRequest(persisted, 70);
        update.setRoomCode(" 103-testta1 ");
        update.setRoomNumber(null);
        mvc.perform(put("/api/v1/rooms/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity").value(70))
                .andExpect(jsonPath("$.data.roomCode").value("103-TESTTA1"))
                .andExpect(jsonPath("$.data.roomNumber").value("103"))
                .andExpect(jsonPath("$.data.roomType").doesNotExist());
        service.changeStatus(id, statusRequest(RoomStatus.INACTIVE));
        var activeOnly = new RoomSearchRequest();
        activeOnly.setKeyword(persisted.getRoomCode());
        activeOnly.setStatus(RoomStatus.ACTIVE);
        assertThat(service.search(activeOnly).getContent()).isEmpty();
        assertThat(service.findById(id).getStatus()).isEqualTo(RoomStatus.INACTIVE);
        service.changeStatus(id, statusRequest(RoomStatus.ACTIVE));
        service.changeStatus(id, statusRequest(RoomStatus.ACTIVE));
        mvc.perform(delete("/api/v1/rooms/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        entityManager.flush();
        assertThat(rooms.findById(id).orElseThrow().isDeleted()).isTrue();
        mvc.perform(get("/api/v1/rooms/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4326));
        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_logs where entity_type = 'ROOM' and entity_id = ?",
                        Long.class,
                        String.valueOf(id)))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"postman_student", "postman_lecturer", "postman_academic_admin", "postman_system_admin"})
    void authenticatedRolesCanUseAllRoomOperationsWhilePermissionsAreDeferred(String username) throws Exception {
        String accessToken = login(username);
        var room = createRoom();
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
            get("/api/v1/rooms"),
            get("/api/v1/rooms/" + room.getId()),
            post("/api/v1/rooms").content(mapper.writeValueAsString(createRequest())),
            put("/api/v1/rooms/" + room.getId()).content(mapper.writeValueAsString(updateRequest(room, 70))),
            patch("/api/v1/rooms/" + room.getId() + "/status")
                    .content(mapper.writeValueAsString(statusRequest(RoomStatus.INACTIVE))),
            delete("/api/v1/rooms/" + room.getId())
        }) {
            mvc.perform(request.header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.success").value(true));
        }
        assertThat(room.isDeleted()).isTrue();
        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_logs where entity_type = 'ROOM' and entity_id = ?",
                        Long.class,
                        room.getId().toString()))
                .isZero();
    }

    @Test
    void sharedFilterStillRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/rooms").header("Authorization", "Bearer " + login("postman_system_admin")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/rooms")).andExpect(status().isUnauthorized());
    }

    @Test
    void a2BlocksDeactivationAndDeletionForAnyScheduleInAnUnfinishedSemester() {
        var room = createRoom();
        var moduleClass = moduleClass(false, 60, ModuleClassStatus.SCHEDULED);
        schedule(room, moduleClass);
        schedule(room, moduleClass);
        assertThatThrownBy(() -> service.changeStatus(room.getId(), statusRequest(RoomStatus.INACTIVE)))
                .isInstanceOfSatisfying(RoomConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ROOM_IN_USE);
                    assertThat(exception.getErrors()).singleElement().satisfies(error -> assertThat(error.getMessage())
                            .contains(moduleClass.getModuleClassCode()));
                });
        assertThatThrownBy(() -> service.delete(room.getId()))
                .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.ROOM_IN_USE));
        assertThat(room.getStatus()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(room.isDeleted()).isFalse();
        var lastDay = moduleClass.getSemester().getEndDate();
        assertThat(schedules.findAll(
                        RoomScheduleSpecification.inUnfinishedSemesters(room.getId(), lastDay), Sort.by("id")))
                .hasSize(2);
        assertThat(schedules.findAll(
                        RoomScheduleSpecification.inUnfinishedSemesters(room.getId(), lastDay.plusDays(1)),
                        Sort.by("id")))
                .isEmpty();
    }

    @Test
    void a3UsesMaximumClassCapacityWithNoStudentsEnrolled() {
        var room = createRoom();
        var moduleClass = moduleClass(false, 60, ModuleClassStatus.SCHEDULED);
        schedule(room, moduleClass);
        assertThat(moduleClass.getCurrentSize()).isZero();
        assertThatThrownBy(() -> service.update(room.getId(), updateRequest(room, 50)))
                .isInstanceOfSatisfying(RoomConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ROOM_CAPACITY_BELOW_CLASS);
                    assertThat(exception.getErrors()).singleElement().satisfies(error -> assertThat(error.getMessage())
                            .contains(moduleClass.getModuleClassCode(), "60"));
                });
        assertThat(room.getCapacity()).isEqualTo(80);
    }

    @Test
    void a3AllowsExactCapacityAndIgnoresCancelledClassesAndRemovedSchedules() {
        var room = createRoom();
        schedule(room, moduleClass(false, 60, ModuleClassStatus.SCHEDULED));
        schedule(room, moduleClass(false, 80, ModuleClassStatus.CANCELLED));
        var removed = schedule(room, moduleClass(false, 80, ModuleClassStatus.SCHEDULED));
        removed.setDeleted(true);
        schedules.saveAndFlush(removed);
        assertThat(service.update(room.getId(), updateRequest(room, 60)).getCapacity())
                .isEqualTo(60);
    }

    @Test
    void endedSemesterSchedulesSurviveDeactivationCapacityReductionAndSoftDeletion() {
        var room = createRoom();
        var savedSchedule = schedule(room, moduleClass(true, 60, ModuleClassStatus.SCHEDULED));
        service.changeStatus(room.getId(), statusRequest(RoomStatus.INACTIVE));
        assertThat(service.update(room.getId(), updateRequest(room, 20)).getCapacity())
                .isEqualTo(20);
        service.delete(room.getId());
        entityManager.flush();
        entityManager.clear();
        var history = schedules.findById(savedSchedule.getId()).orElseThrow();
        assertThat(history.getRoom().getRoomCode()).isEqualTo(room.getRoomCode());
        assertThat(history.getRoom().getRoomName()).isEqualTo("Phòng kiểm tra");
        assertThat(history.getRoom().isDeleted()).isTrue();
        assertThat(history.getRoom().getStatus()).isEqualTo(RoomStatus.INACTIVE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void databaseRejectsNewSchedulesForInactiveOrDeletedRooms(boolean deleted) {
        var room = createRoom();
        var moduleClass = moduleClass(false, 60, ModuleClassStatus.SCHEDULED);
        if (deleted) service.delete(room.getId());
        else service.changeStatus(room.getId(), statusRequest(RoomStatus.INACTIVE));
        assertThatThrownBy(() -> schedule(room, moduleClass))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("Room is not available for scheduling");
    }

    @Test
    void databaseRejectsNewSchedulesWhenRoomCapacityIsTooSmall() {
        var room = createRoom();
        service.update(room.getId(), updateRequest(room, 40));
        var moduleClass = moduleClass(false, 60, ModuleClassStatus.SCHEDULED);
        assertThatThrownBy(() -> schedule(room, moduleClass))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("Room capacity is below class capacity");
    }

    @Test
    void searchesRoomNameCampusAndBuildingCaseInsensitively() {
        var room = createRoom();
        var query = new RoomSearchRequest();
        query.setKeyword("phòng kiểm tra");
        query.setCampus("cs1");
        query.setBuildingCode("ta1");
        query.setMinCapacity(80);
        var second = createRequest();
        second.setRoomNumber(room.getRoomNumber());
        second.setBuildingCode("TA2");
        second.setRoomCode(room.getRoomNumber() + "-TA2");
        second.setCampus("CS2");
        assertThat(service.create(second).getCampus()).isEqualTo("CS2");
        assertThat(service.search(query).getContent())
                .extracting(RoomResponse::getId)
                .containsExactly(room.getId());
    }

    private String login(String username) {
        return auth.login(new LoginRequest(username, "Postman@123")).getAccessToken();
    }

    private Room createRoom() {
        return rooms.findById(service.create(createRequest()).getId()).orElseThrow();
    }

    private CreateRoomRequest createRequest() {
        var request = new CreateRoomRequest();
        request.setRoomCode(SEQUENCES.incrementAndGet() + "-TA1");
        request.setRoomName("Phòng kiểm tra");
        request.setCampus("CS1");
        request.setBuildingCode("TA1");
        request.setCapacity(80);
        return request;
    }

    private UpdateRoomRequest updateRequest(Room room, int capacity) {
        var request = new UpdateRoomRequest();
        request.setRoomCode(room.getRoomCode());
        request.setRoomName(room.getRoomName());
        request.setCampus(room.getCampus());
        request.setBuildingCode(room.getBuildingCode());
        request.setRoomNumber(room.getRoomNumber());
        request.setCapacity(capacity);
        return request;
    }

    private ChangeRoomStatusRequest statusRequest(RoomStatus status) {
        var request = new ChangeRoomStatusRequest();
        request.setStatus(status);
        return request;
    }

    private ModuleClass moduleClass(boolean ended, int capacity, ModuleClassStatus status) {
        var today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var semester = semesters.findAll().stream()
                .filter(value -> ended == value.getEndDate().isBefore(today))
                .findFirst()
                .orElseThrow();
        var subject = subjects.findAll().getFirst();
        int sequence = SEQUENCES.incrementAndGet();
        return classes.saveAndFlush(ModuleClass.builder()
                .semester(semester)
                .subject(subject)
                .moduleClassCode("TEST_ROOM_CLASS_" + sequence)
                .classSequence(sequence)
                .maxCapacity(capacity)
                .currentSize(0)
                .status(status)
                .build());
    }

    private ClassSchedule schedule(Room room, ModuleClass moduleClass) {
        return schedules.saveAndFlush(ClassSchedule.builder()
                .room(room)
                .moduleClass(moduleClass)
                .dayOfWeek(2)
                .lessonOrderStart(1)
                .lessonOrderEnd(3)
                .startDate(moduleClass.getSemester().getStartDate())
                .endDate(moduleClass.getSemester().getStartDate().plusDays(1))
                .build());
    }
}
