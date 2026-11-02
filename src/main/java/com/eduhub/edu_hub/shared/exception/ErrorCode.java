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

    // Curriculum
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
