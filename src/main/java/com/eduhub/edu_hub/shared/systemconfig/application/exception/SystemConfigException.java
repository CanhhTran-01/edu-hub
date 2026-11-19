package com.eduhub.edu_hub.shared.systemconfig.application.exception;

import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.util.List;
import lombok.Getter;

@Getter
public class SystemConfigException extends BaseException {
    private final List<FieldErrorDetail> fieldErrors;

    public SystemConfigException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public SystemConfigException(ErrorCode errorCode, List<FieldErrorDetail> fieldErrors) {
        super(errorCode);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public static SystemConfigException unavailable(SystemConfigKey key) {
        return new SystemConfigException(
                ErrorCode.SYSTEM_CONFIG_UNAVAILABLE,
                List.of(new FieldErrorDetail(key.propertyName(), "Tham số chưa được cấu hình hợp lệ")));
    }
}
