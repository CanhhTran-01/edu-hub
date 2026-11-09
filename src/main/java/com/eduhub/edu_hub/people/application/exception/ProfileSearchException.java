package com.eduhub.edu_hub.people.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi tiêu chí tìm kiếm hoặc phân trang hồ sơ sinh viên, giảng viên. */
public class ProfileSearchException extends BaseException {
    public ProfileSearchException() {
        super(ErrorCode.VALIDATION_ERROR);
    }
}
