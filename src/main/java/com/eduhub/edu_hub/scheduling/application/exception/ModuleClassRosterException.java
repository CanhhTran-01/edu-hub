package com.eduhub.edu_hub.scheduling.application.exception;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

/** Lỗi truy cập danh sách lớp, lựa chọn cột hoặc tạo tệp danh sách sinh viên. */
@Getter
public class ModuleClassRosterException extends BaseException {
    private final List<FieldErrorDetail> errors;

    public ModuleClassRosterException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public ModuleClassRosterException(ErrorCode errorCode, List<FieldErrorDetail> errors) {
        super(errorCode);
        this.errors = errors;
    }

    public ModuleClassRosterException(ErrorCode errorCode, Throwable cause) {
        this(errorCode);
        initCause(cause);
    }
}
