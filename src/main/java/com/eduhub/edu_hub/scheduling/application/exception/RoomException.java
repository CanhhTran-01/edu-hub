package com.eduhub.edu_hub.scheduling.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi dữ liệu và điều kiện quản lý phòng học, không thay đổi quy tắc xóa hoặc sử dụng phòng. */
public class RoomException extends BaseException {
    public RoomException(ErrorCode errorCode) {
        super(errorCode);
    }
}
