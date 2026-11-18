package com.eduhub.edu_hub.scheduling.application.facade.impl;

import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import com.eduhub.edu_hub.scheduling.domain.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomCapacityFacadeImpl implements SchedulingFacade.RoomCapacity {
    private final RoomRepository roomRepository;

    @Override
    @Transactional(readOnly = true)
    public long countSuitableRooms(int requiredCapacity) {
        return roomRepository.countByDeletedFalseAndStatusAndCapacityGreaterThanEqual(
                RoomStatus.ACTIVE, requiredCapacity);
    }
}
