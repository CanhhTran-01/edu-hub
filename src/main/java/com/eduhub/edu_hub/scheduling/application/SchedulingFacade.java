package com.eduhub.edu_hub.scheduling.application;

public interface SchedulingFacade {
    interface RoomCapacity {
        long countSuitableRooms(int requiredCapacity);
    }
}
