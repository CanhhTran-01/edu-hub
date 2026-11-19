package com.eduhub.edu_hub.scheduling.api.dto.response;

/** Giới hạn hiện hành cho màn tạo/sửa lớp học phần; sức chứa đã lưu trên từng lớp vẫn giữ riêng. */
public record ModuleClassCapacityResponse(int defaultCapacity, int maximumCapacity) {}
