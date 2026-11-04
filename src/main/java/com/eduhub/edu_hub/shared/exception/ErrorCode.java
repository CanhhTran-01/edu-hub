package com.eduhub.edu_hub.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // General
    UNEXPECTED_ERROR(1000, "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR(1001, "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER(1002, "Thiếu tham số bắt buộc", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED(1003, "Phương thức HTTP không được hỗ trợ", HttpStatus.METHOD_NOT_ALLOWED),

    // Identity
    USER_NOT_FOUND(4002, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS(4003, "Sai thông tin đăng nhập", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(4004, "Tài khoản đã bị khóa", HttpStatus.FORBIDDEN),
    PASSWORD_POLICY_VIOLATION(4005, "Mật khẩu không đạt chính sách bảo mật", HttpStatus.BAD_REQUEST),
    FIRST_LOGIN_REQUIRED(4006, "Bắt buộc đổi mật khẩu lần đầu", HttpStatus.FORBIDDEN),
    ACCOUNT_EMAIL_MISSING(4007, "Tài khoản chưa có email nhận thông báo", HttpStatus.BAD_REQUEST),
    PASSWORD_EMAIL_FAILED(4008, "Không gửi được email mật khẩu tạm", HttpStatus.BAD_GATEWAY),

    // People
    STUDENT_NOT_FOUND(4101, "Không tìm thấy sinh viên", HttpStatus.NOT_FOUND),
    LECTURER_NOT_FOUND(4102, "Không tìm thấy giảng viên", HttpStatus.NOT_FOUND),
    DUPLICATE_STUDENT_CODE(4103, "MSSV đã tồn tại", HttpStatus.CONFLICT),
    DUPLICATE_LECTURER_CODE(4104, "Mã giảng viên đã tồn tại", HttpStatus.CONFLICT),
    CLASS_CAPACITY_EXCEEDED(4105, "Lớp đã đầy sức chứa", HttpStatus.BAD_REQUEST),
    ADMISSION_BATCH_NOT_FOUND(4106, "Không tìm thấy batch nhập học", HttpStatus.NOT_FOUND),
    ADMISSION_BATCH_ALREADY_CONFIRMED(4107, "Batch nhập học đã được xác nhận", HttpStatus.CONFLICT),
    ADMISSION_CAPACITY_CONFIRMATION_REQUIRED(4108, "Cần xử lý hoặc chấp nhận cảnh báo sức chứa", HttpStatus.CONFLICT),
    INVALID_STUDENT_CODE_MAJOR(4109, "Mã ngành phải gồm đúng 2 ký tự chữ", HttpStatus.BAD_REQUEST),
    INVALID_STUDENT_CODE_COHORT(4110, "Mã khóa không đúng định dạng sinh MSSV", HttpStatus.BAD_REQUEST),
    INVALID_STUDENT_CODE_CLASS(4111, "Mã lớp hành chính không đúng định dạng L01", HttpStatus.BAD_REQUEST),
    STUDENT_CODE_SEQUENCE_EXCEEDED(4112, "Số thứ tự MSSV trong lớp đã vượt 99", HttpStatus.CONFLICT),
    DUPLICATE_STUDENT_EMAIL(4113, "Email sinh viên đã tồn tại", HttpStatus.CONFLICT),
    DUPLICATE_STUDENT_CITIZEN_ID(4114, "CCCD sinh viên đã tồn tại", HttpStatus.CONFLICT),
    STUDENT_CLASS_MISMATCH(4115, "Ngành, hệ đào tạo hoặc khóa không khớp với lớp", HttpStatus.BAD_REQUEST),

    // Curriculum
    INVALID_TARGET_GPA(4120, "GPA mục tiêu phải từ 0 đến 4", HttpStatus.BAD_REQUEST),
    FACULTY_NOT_ACTIVE(4116, "Khoa không tồn tại hoặc đã ngừng hoạt động", HttpStatus.BAD_REQUEST),
    DUPLICATE_LECTURER_EMAIL(4117, "Email giảng viên đã tồn tại", HttpStatus.CONFLICT),
    DUPLICATE_LECTURER_CITIZEN_ID(4118, "CCCD giảng viên đã tồn tại", HttpStatus.CONFLICT),
    LECTURER_CODE_EXHAUSTED(4119, "Đã hết dải mã giảng viên GV 6 chữ số", HttpStatus.CONFLICT),
    SUBJECT_NOT_FOUND(4201, "Không tìm thấy học phần", HttpStatus.NOT_FOUND),
    CURRICULUM_NOT_FOUND(4202, "Không tìm thấy chương trình đào tạo", HttpStatus.NOT_FOUND),
    PREREQUISITE_CYCLE(4203, "Phát hiện chu trình tiên quyết", HttpStatus.BAD_REQUEST),
    SUBJECT_IN_USE(4204, "Học phần đang được sử dụng", HttpStatus.CONFLICT),
    DUPLICATE_SUBJECT_CODE(4205, "Mã học phần đã tồn tại", HttpStatus.CONFLICT),
    DUPLICATE_SUBJECT_NAME(4206, "Tên học phần đã tồn tại", HttpStatus.CONFLICT),
    SUBJECT_NOT_ACTIVE(4207, "Chỉ được cập nhật học phần đang sử dụng", HttpStatus.CONFLICT),
    SUBJECT_CODE_IMMUTABLE(4208, "Không được thay đổi mã học phần", HttpStatus.CONFLICT),
    SUBJECT_ACTION_NOT_CONFIRMED(4209, "Cần xác nhận thao tác trước khi thực hiện", HttpStatus.BAD_REQUEST),
    PREREQUISITE_SELF_REFERENCE(4210, "Học phần không thể là tiên quyết của chính nó", HttpStatus.BAD_REQUEST),
    PREREQUISITE_NOT_ACTIVE(4211, "Môn tiên quyết phải ở trạng thái đang sử dụng", HttpStatus.BAD_REQUEST),

    // Scheduling
    MODULE_CLASS_NOT_FOUND(4301, "Không tìm thấy lớp học phần", HttpStatus.NOT_FOUND),
    SEMESTER_LOCKED(4302, "Kỳ học đã khóa", HttpStatus.BAD_REQUEST),
    ROOM_CONFLICT(4303, "Xung đột phòng học", HttpStatus.CONFLICT),
    LECTURER_CONFLICT(4304, "Giảng viên bị trùng lịch", HttpStatus.CONFLICT),
    SEMESTER_NOT_FOUND(4305, "Không tìm thấy kỳ học", HttpStatus.NOT_FOUND),
    DUPLICATE_SEMESTER_NAME(4306, "Tên kỳ học đã tồn tại", HttpStatus.CONFLICT),
    INVALID_SEMESTER_NAME(
            4307,
            "Tên kỳ học phải theo mẫu Học kì 1 năm học 2026-2027, số kỳ dương và hai năm liên tiếp",
            HttpStatus.BAD_REQUEST),
    INVALID_SEMESTER_DATES(4308, "Ngày bắt đầu kỳ học phải trước ngày kết thúc", HttpStatus.BAD_REQUEST),
    INVALID_REGISTRATION_PERIOD(
            4309,
            "Ngày mở đăng ký phải trước ngày đóng; cả hai phải trước ngày bắt đầu kỳ học",
            HttpStatus.BAD_REQUEST),
    SEMESTER_DATE_OVERLAP(4310, "Thời gian học chồng lấn với kỳ học khác", HttpStatus.CONFLICT),
    SEMESTER_REGISTRATION_OVERLAP(4311, "Thời gian đăng ký chồng lấn với kỳ học khác", HttpStatus.CONFLICT),
    SEMESTER_ENDED(4312, "Kỳ học đã kết thúc, chỉ được xem thông tin", HttpStatus.CONFLICT),
    MODULE_CLASS_CONFIRMATION_REQUIRED(4313, "Học phần đã có lớp trong kỳ, cần xác nhận mở thêm", HttpStatus.CONFLICT),
    SUBJECT_NOT_OPENABLE(4314, "Chỉ được mở lớp cho học phần đang sử dụng", HttpStatus.CONFLICT),
    DUPLICATE_BATCH_SUBJECT(4316, "Một học phần chỉ được xuất hiện một lần trong lô", HttpStatus.BAD_REQUEST),
    PLANNING_DATA_UNAVAILABLE(
            4317,
            "Nguồn dữ liệu sinh viên, chương trình đào tạo hoặc lịch sử học chưa sẵn sàng/đầy đủ",
            HttpStatus.SERVICE_UNAVAILABLE),
    MODULE_CLASS_CODE_CONFLICT(
            4318, "Mã hoặc STT lớp học phần đã được sử dụng, hãy tải lại dữ liệu", HttpStatus.CONFLICT),
    PLANNING_SEMESTER_UNSUPPORTED(
            4319, "Thống kê hiện hỗ trợ năm học có hai kỳ với mã HK_1/HK_2", HttpStatus.BAD_REQUEST),
    SEMESTER_DATE_CHANGE_CONFIRMATION_REQUIRED(
            4320, "Kỳ có lớp chưa hủy, cần xác nhận thay đổi ngày học", HttpStatus.CONFLICT),
    MODULE_CLASS_CANCELLED(4321, "Lớp học phần đã hủy, chỉ được xem thông tin", HttpStatus.CONFLICT),
    MODULE_CLASS_CAPACITY_BELOW_SIZE(4322, "Sức chứa mới không được nhỏ hơn sĩ số hiện có", HttpStatus.CONFLICT),
    MODULE_CLASS_HAS_ENROLLMENTS(4323, "Lớp đã phát sinh đăng ký, không được hủy", HttpStatus.CONFLICT),
    MODULE_CLASS_ENROLLMENT_DATA_UNAVAILABLE(
            4325, "Chưa có nguồn lịch sử đăng ký đầy đủ để kiểm tra hủy lớp", HttpStatus.SERVICE_UNAVAILABLE),

    // Enrollment
    ENROLLMENT_NOT_FOUND(4401, "Không tìm thấy đăng ký", HttpStatus.NOT_FOUND),
    REGISTRATION_CLOSED(4402, "Đã hết thời gian đăng ký", HttpStatus.BAD_REQUEST),
    PREREQUISITE_NOT_MET(4403, "Chưa đủ điều kiện tiên quyết", HttpStatus.BAD_REQUEST),
    CAPACITY_FULL(4404, "Lớp học phần đã hết chỗ", HttpStatus.CONFLICT),
    ALREADY_ENROLLED(4405, "Đã đăng ký lớp học phần này", HttpStatus.CONFLICT),

    // Academic
    GRADE_LOCKED(4501, "Điểm đã bị khóa", HttpStatus.BAD_REQUEST),
    INVALID_SCORE(4502, "Điểm không hợp lệ", HttpStatus.BAD_REQUEST),

    // AI
    KNOWLEDGE_SOURCE_NOT_FOUND(4601, "Không tìm thấy nguồn tri thức", HttpStatus.NOT_FOUND),
    CHAT_SESSION_NOT_FOUND(4602, "Không tìm thấy phiên hội thoại", HttpStatus.NOT_FOUND),

    // Security
    UNAUTHENTICATED(4000, "Vui lòng đăng nhập", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(4001, "Không có quyền truy cập", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
