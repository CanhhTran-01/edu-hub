package com.eduhub.edu_hub.scheduling.domain.enums;

/**
 * Trạng thái vòng đời của một kế hoạch thời khóa biểu (timetable plan).
 * Plan đi từ lúc xếp hàng chờ, chạy solver, đến khi có kết quả và được áp dụng.
 */
public enum TimetablePlanState {
    QUEUED, // Đang xếp hàng chờ chạy
    RUNNING, // Solver đang chạy
    FEASIBLE, // Có lời giải khả thi, có thể áp dụng
    NO_FEASIBLE_SOLUTION, // Chạy xong nhưng không có lời giải thỏa ràng buộc cứng

    STOP_REQUESTED, // Đã yêu cầu dừng, chờ solver dừng hẳn
    STOPPED, // Đã dừng theo yêu cầu người dùng

    FAILED, // Gặp lỗi khi chạy
    INTERRUPTED, // Bị ngắt ngoài ý muốn (ví dụ: server restart)

    APPLIED; // Đã áp dụng vào thời khóa biểu chính thức

    // Đang xử lý (chưa có kết quả cuối)
    public boolean isActive() {
        return this == QUEUED || this == RUNNING || this == STOP_REQUESTED;
    }

    // Được phép áp dụng: đã có lời giải dùng được
    public boolean allowsApplying() {
        return this == FEASIBLE || this == STOPPED;
    }
}
