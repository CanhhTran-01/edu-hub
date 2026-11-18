package com.eduhub.edu_hub.scheduling.domain.enums;

public enum ModuleClassStatus {
    UNSCHEDULED, // Chưa xếp lịch
    SCHEDULED, // Đã xếp lịch
    CANCELLED, // Đã hủy
    OPEN, // Mở đăng ký
    CLOSED, // Đã đóng đăng ký, chốt danh sách
    COMPLETED // Đã kết thúc toàn bộ buổi học
}
