package com.eduhub.edu_hub.scheduling.domain.repository;

import com.eduhub.edu_hub.scheduling.domain.model.Room;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {
    Optional<Room> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Room> findForUpdateByIdAndDeletedFalse(Long id);

    boolean existsByRoomCode(String roomCode);

    boolean existsByRoomCodeAndIdNot(String roomCode, Long id);

    boolean existsByCampusAndBuildingCodeAndRoomNumber(String campus, String buildingCode, String roomNumber);

    boolean existsByCampusAndBuildingCodeAndRoomNumberAndIdNot(
            String campus, String buildingCode, String roomNumber, Long id);
}
