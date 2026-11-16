package com.eduhub.edu_hub.scheduling.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ModuleClassExportColumn {
    STUDENT_CODE,
    FULL_NAME,
    COMPONENT_SCORE_1,
    COMPONENT_SCORE_2,
    PROCESS_SCORE,
    NOTE;

    @JsonCreator
    public static ModuleClassExportColumn parseColumn(Object columnName) {
        // Chỉ nhận mã cột dạng chuỗi, không cho JSON number chọn enum theo thứ tự khai báo.
        if (!(columnName instanceof String name))
            throw new IllegalArgumentException("Mã cột xuất phải là chuỗi hợp lệ");
        return valueOf(name);
    }
}
