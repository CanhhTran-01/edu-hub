package com.eduhub.edu_hub.scheduling.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.scheduling.api.dto.request.ChangeRoomStatusRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.CreateRoomRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.RoomSearchRequest;
import com.eduhub.edu_hub.scheduling.api.dto.request.UpdateRoomRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.RoomResponse;
import com.eduhub.edu_hub.scheduling.application.exception.RoomConflictException;
import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import com.eduhub.edu_hub.scheduling.domain.model.ClassSchedule;
import com.eduhub.edu_hub.scheduling.domain.model.ModuleClass;
import com.eduhub.edu_hub.scheduling.domain.model.Room;
import com.eduhub.edu_hub.scheduling.domain.model.Semester;
import com.eduhub.edu_hub.scheduling.domain.repository.ClassScheduleRepository;
import com.eduhub.edu_hub.scheduling.domain.repository.RoomRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class RoomApplicationServiceTest {
    @Mock
    private RoomRepository repository;

    @Mock
    private ClassScheduleRepository schedules;

    private RoomApplicationService service;

    @BeforeEach
    void setUp() {
        service = new RoomApplicationService(repository, schedules);
    }

    @Test
    void createsNormalizedActiveRoomWithCapacityIndependentOfClassLimit() {
        var request = createRequest();
        request.setRoomCode("  102-ta1  ");
        request.setBuildingCode(" ta1 ");
        request.setRoomNumber(" 102 ");
        request.setCapacity(120);
        when(repository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(1L);
            assertThat(room.isDeleted()).isFalse();
            assertThat(room.getCreatedAt()).isNotNull();
            assertThat(room.getUpdatedAt()).isEqualTo(room.getCreatedAt());
            return room;
        });

        var result = service.create(request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRoomCode()).isEqualTo("102-TA1");
        assertThat(result.getBuildingCode()).isEqualTo("TA1");
        assertThat(result.getRoomNumber()).isEqualTo("102");
        assertThat(result.getCapacity()).isEqualTo(120);
        assertThat(result.getStatus()).isEqualTo(RoomStatus.ACTIVE);
    }

    @Test
    void rejectsCodeReservedByAnyRoomIncludingSoftDeleted() {
        when(repository.existsByRoomCode("102-TA1")).thenReturn(true);

        assertError(() -> service.create(createRequest()), ErrorCode.DUPLICATE_ROOM_CODE);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsReservedPhysicalLocation() {
        when(repository.existsByCampusAndBuildingCodeAndRoomNumber("", "TA1", "102"))
                .thenReturn(true);

        assertError(() -> service.create(createRequest()), ErrorCode.DUPLICATE_ROOM_LOCATION);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNonPositiveCapacityAndBlankIdentifiers() {
        var request = createRequest();
        request.setCapacity(0);
        assertError(() -> service.create(request), ErrorCode.VALIDATION_ERROR);
        request.setCapacity(80);
        request.setRoomCode("   ");
        assertError(() -> service.create(request), ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void returnsInactiveRoomDetails() {
        var room = room();
        room.setStatus(RoomStatus.INACTIVE);
        when(repository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(room));

        assertThat(service.findById(1L).getStatus()).isEqualTo(RoomStatus.INACTIVE);
    }

    @Test
    void missingOrHiddenRoomReturns404ForReadAndAllMutations() {
        assertError(() -> service.findById(99L), ErrorCode.ROOM_NOT_FOUND);
        assertError(() -> service.update(99L, updateRequest()), ErrorCode.ROOM_NOT_FOUND);
        assertError(() -> service.changeStatus(99L, statusRequest(RoomStatus.INACTIVE)), ErrorCode.ROOM_NOT_FOUND);
        assertError(() -> service.delete(99L), ErrorCode.ROOM_NOT_FOUND);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updatePreservesIdStatusAndCreationTimeAndExcludesItselfFromUniqueChecks() {
        var room = room();
        room.setStatus(RoomStatus.INACTIVE);
        var createdAt = java.time.LocalDateTime.parse("2026-01-01T00:00:00");
        room.setCreatedAt(createdAt);
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(room));
        when(repository.saveAndFlush(room)).thenReturn(room);
        var request = updateRequest();
        request.setRoomCode(" 102-ta1 ");
        request.setCapacity(100);

        var result = service.update(1L, request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRoomCode()).isEqualTo("102-TA1");
        assertThat(result.getCapacity()).isEqualTo(100);
        assertThat(result.getStatus()).isEqualTo(RoomStatus.INACTIVE);
        assertThat(room.getCreatedAt()).isEqualTo(createdAt);
        verify(repository).existsByRoomCodeAndIdNot("102-TA1", 1L);
        verify(repository).existsByCampusAndBuildingCodeAndRoomNumberAndIdNot("", "TA1", "102", 1L);
    }

    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"DUPLICATE_ROOM_CODE", "DUPLICATE_ROOM_LOCATION"})
    void rejectedUpdateDoesNotPartiallyChangeEntity(ErrorCode error) {
        var room = room();
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(room));
        var request = updateRequest();
        request.setRoomCode("202-TB2");
        request.setBuildingCode("TB2");
        request.setRoomNumber("202");
        if (error == ErrorCode.DUPLICATE_ROOM_CODE) {
            when(repository.existsByRoomCodeAndIdNot("202-TB2", 1L)).thenReturn(true);
        } else {
            when(repository.existsByCampusAndBuildingCodeAndRoomNumberAndIdNot("", "TB2", "202", 1L))
                    .thenReturn(true);
        }

        assertError(() -> service.update(1L, request), error);

        assertThat(room.getRoomCode()).isEqualTo("102-TA1");
        assertThat(room.getBuildingCode()).isEqualTo("TA1");
        assertThat(room.getRoomNumber()).isEqualTo("102");
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(RoomStatus.class)
    void supportsDeactivationAndReactivation(RoomStatus target) {
        var room = room();
        room.setStatus(target == RoomStatus.ACTIVE ? RoomStatus.INACTIVE : RoomStatus.ACTIVE);
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(room));
        when(repository.saveAndFlush(room)).thenReturn(room);

        assertThat(service.changeStatus(1L, statusRequest(target)).getStatus()).isEqualTo(target);
        assertThat(room.isDeleted()).isFalse();
        assertThat(room.getUpdatedAt()).isNotNull();
    }

    @Test
    void repeatedStatusChangeIsIdempotent() {
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(room()));

        assertThat(service.changeStatus(1L, statusRequest(RoomStatus.ACTIVE)).getStatus())
                .isEqualTo(RoomStatus.ACTIVE);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void deletesSoftlyWithoutReleasingCodeOrLocation() {
        var room = room();
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(room));

        service.delete(1L);

        assertThat(room.isDeleted()).isTrue();
        assertThat(room.getRoomCode()).isEqualTo("102-TA1");
        assertThat(room.getBuildingCode()).isEqualTo("TA1");
        assertThat(room.getRoomNumber()).isEqualTo("102");
        verify(repository).saveAndFlush(room);
        verify(repository, never()).delete(any(Room.class));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void searchMapsPaginatedRoomsWithoutExposingEntity() {
        var request = new RoomSearchRequest();
        request.setPage(1);
        request.setSize(5);
        var pageable = PageRequest.of(1, 5, Sort.by("campus", "buildingCode", "roomNumber", "id"));
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Room>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(room()), pageable, 6));

        var result = service.search(request);

        assertThat(result.getPageNumber()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getContent()).extracting(RoomResponse::getRoomCode).containsExactly("102-TA1");
    }

    @Test
    void a2RejectsDeactivationAndDeletionAndDeduplicatesClassesAcrossScheduleRows() {
        var room = room();
        var moduleClass = scheduledClass(20L, 60);
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(room));
        when(schedules.findAll(org.mockito.ArgumentMatchers.<Specification<ClassSchedule>>any(), any(Sort.class)))
                .thenReturn(List.of(
                        ClassSchedule.builder().moduleClass(moduleClass).build(),
                        ClassSchedule.builder().moduleClass(moduleClass).build()));

        for (Runnable action : List.<Runnable>of(
                () -> service.changeStatus(1L, statusRequest(RoomStatus.INACTIVE)), () -> service.delete(1L))) {
            assertThatThrownBy(action::run).isInstanceOfSatisfying(RoomConflictException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ROOM_IN_USE);
                assertThat(exception.getErrors()).singleElement().satisfies(error -> {
                    assertThat(error.getField()).isEqualTo("moduleClasses[20]");
                    assertThat(error.getMessage()).contains("MC_TEST_20", "60");
                });
            });
        }
        assertThat(room.getStatus()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(room.isDeleted()).isFalse();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void a3ComparesAgainstClassMaximumCapacityInsteadOfCurrentEnrollment() {
        var room = room();
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(room));
        when(schedules.findAll(org.mockito.ArgumentMatchers.<Specification<ClassSchedule>>any(), any(Sort.class)))
                .thenReturn(List.of(
                        ClassSchedule.builder()
                                .moduleClass(scheduledClass(20L, 60))
                                .build(),
                        ClassSchedule.builder()
                                .moduleClass(scheduledClass(21L, 30))
                                .build()));
        var update = updateRequest();
        update.setCapacity(50);
        assertThatThrownBy(() -> service.update(1L, update))
                .isInstanceOfSatisfying(RoomConflictException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ROOM_CAPACITY_BELOW_CLASS);
                    assertThat(exception.getErrors()).singleElement().satisfies(error -> assertThat(error.getMessage())
                            .contains("MC_TEST_20"));
                });
        assertThat(room.getCapacity()).isEqualTo(80);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void allowsReductionToTheExactClassMaximum() {
        var room = room();
        when(repository.findForUpdateByIdAndDeletedFalse(1L)).thenReturn(Optional.of(room));
        when(schedules.findAll(org.mockito.ArgumentMatchers.<Specification<ClassSchedule>>any(), any(Sort.class)))
                .thenReturn(List.of(ClassSchedule.builder()
                        .moduleClass(scheduledClass(20L, 60))
                        .build()));
        when(repository.saveAndFlush(room)).thenReturn(room);
        var update = updateRequest();
        update.setCapacity(60);
        assertThat(service.update(1L, update).getCapacity()).isEqualTo(60);
    }

    @Test
    void acceptsUseCaseFieldsWithoutSeparateRoomNumberAndNormalizesCampus() {
        var request = createRequest();
        request.setRoomCode("102-ta1");
        request.setRoomName("  Phòng học 102  ");
        request.setCampus(" cs1 ");
        request.setRoomNumber(null);
        when(repository.saveAndFlush(any(Room.class))).thenAnswer(call -> {
            Room saved = call.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        var result = service.create(request);
        assertThat(result.getRoomCode()).isEqualTo("102-TA1");
        assertThat(result.getRoomName()).isEqualTo("Phòng học 102");
        assertThat(result.getCampus()).isEqualTo("CS1");
        assertThat(result.getRoomNumber()).isEqualTo("102");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"TA1-102", "CS1-102-TA1", "102TA1", "102_TA1", "102--TA1", "102-TA1!", "102 TA1", "102A-TA1"})
    void a1RejectsInvalidCodeFormat(String code) {
        var request = createRequest();
        request.setRoomCode(code);
        assertError(() -> service.create(request), ErrorCode.INVALID_ROOM_CODE);
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"102,TB2,buildingCode", "103,TA1,roomNumber"})
    void rejectsCodeThatDoesNotMatchRoomNumberOrBuildingOnCreateAndUpdate(
            String number, String building, String field) {
        var create = createRequest();
        create.setRoomNumber(number);
        create.setBuildingCode(building);
        var update = updateRequest();
        update.setRoomNumber(number);
        update.setBuildingCode(building);

        for (Runnable action : List.<Runnable>of(() -> service.create(create), () -> service.update(1L, update))) {
            assertThatThrownBy(action::run).isInstanceOfSatisfying(RoomConflictException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                assertThat(exception.getErrors()).singleElement().satisfies(error -> {
                    assertThat(error.getField()).isEqualTo(field);
                    assertThat(error.getMessage()).contains("khớp");
                });
            });
        }
        org.mockito.Mockito.verifyNoInteractions(repository, schedules);
    }

    private ModuleClass scheduledClass(Long id, int maxCapacity) {
        var moduleClass = ModuleClass.builder()
                .moduleClassCode("MC_TEST_" + id)
                .maxCapacity(maxCapacity)
                .currentSize(0)
                .semester(Semester.builder()
                        .semesterName("Học kì 1 năm học 2026-2027")
                        .build())
                .build();
        moduleClass.setId(id);
        return moduleClass;
    }

    private CreateRoomRequest createRequest() {
        var request = new CreateRoomRequest();
        request.setRoomCode("102-TA1");
        request.setRoomName("Phòng 102");
        request.setBuildingCode("TA1");
        request.setRoomNumber("102");
        request.setCapacity(80);
        return request;
    }

    private UpdateRoomRequest updateRequest() {
        var request = new UpdateRoomRequest();
        request.setRoomCode("102-TA1");
        request.setRoomName("Phòng 102");
        request.setBuildingCode("TA1");
        request.setRoomNumber("102");
        request.setCapacity(80);
        return request;
    }

    private ChangeRoomStatusRequest statusRequest(RoomStatus status) {
        var request = new ChangeRoomStatusRequest();
        request.setStatus(status);
        return request;
    }

    private Room room() {
        var room = Room.builder()
                .roomCode("102-TA1")
                .roomName("Phòng 102")
                .buildingCode("TA1")
                .roomNumber("102")
                .capacity(80)
                .build();
        room.setId(1L);
        return room;
    }

    private void assertError(Runnable action, ErrorCode error) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(error));
    }
}
