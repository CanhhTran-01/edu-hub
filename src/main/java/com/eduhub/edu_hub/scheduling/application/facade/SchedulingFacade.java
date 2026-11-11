package com.eduhub.edu_hub.scheduling.application.facade;

public interface SchedulingFacade {
    interface RoomCapacity {
        long countSuitableRooms(int requiredCapacity);
    }
}
