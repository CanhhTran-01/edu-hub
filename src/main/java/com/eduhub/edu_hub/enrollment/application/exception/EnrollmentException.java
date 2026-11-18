package com.eduhub.edu_hub.enrollment.application.exception;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

/** Lỗi điều kiện đăng ký, dữ liệu nguồn hoặc cấu hình của module Enrollment. */
@Getter
public class EnrollmentException extends BaseException {
    private final List<FieldErrorDetail> errors;

    public EnrollmentException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public EnrollmentException(ErrorCode errorCode, List<FieldErrorDetail> errors) {
        super(errorCode);
        this.errors = List.copyOf(errors);
    }
}
