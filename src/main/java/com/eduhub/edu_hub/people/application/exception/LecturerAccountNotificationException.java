package com.eduhub.edu_hub.people.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi tìm hồ sơ hoặc tài khoản liên kết khi gửi/gửi lại thông tin tài khoản giảng viên. */
public class LecturerAccountNotificationException extends BaseException {
    public LecturerAccountNotificationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
