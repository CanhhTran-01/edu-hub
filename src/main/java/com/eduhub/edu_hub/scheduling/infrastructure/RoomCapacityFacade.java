package com.eduhub.edu_hub.scheduling.infrastructure;

import com.eduhub.edu_hub.scheduling.application.SchedulingFacade;
import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import com.eduhub.edu_hub.scheduling.domain.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomCapacityFacade implements SchedulingFacade.RoomCapacity {
    private final RoomRepository rooms;

    @Override
    @Transactional(readOnly = true)
    public long countSuitableRooms(int requiredCapacity) {
        return rooms.countByDeletedFalseAndStatusAndCapacityGreaterThanEqual(RoomStatus.ACTIVE, requiredCapacity);
    }
}
