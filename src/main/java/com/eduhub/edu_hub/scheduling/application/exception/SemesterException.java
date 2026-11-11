package com.eduhub.edu_hub.scheduling.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi kiểm tra, tra cứu và thay đổi kỳ học; giữ nguyên mã lỗi và HTTP contract hiện tại. */
public class SemesterException extends BaseException {
    public SemesterException(ErrorCode errorCode) {
        super(errorCode);
    }
}
