package com.eduhub.edu_hub.curriculum.application.exception;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

/** Lỗi vòng đời học phần và thay đổi khối lượng giảng dạy khi môn đang được sử dụng. */
public class SubjectException extends BaseException {
    public SubjectException(ErrorCode errorCode) {
        super(errorCode);
    }
}
