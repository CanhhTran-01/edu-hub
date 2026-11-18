package com.eduhub.edu_hub.enrollment.api.dto.request;

import com.eduhub.edu_hub.enrollment.api.serialization.EnrollmentIdDeserializer;
import com.eduhub.edu_hub.enrollment.api.serialization.ProcessScoreDeserializer;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Bỏ field giữ nguyên, null xóa; client không được sửa danh tính hoặc điểm do hệ thống tính. */
public class UpdateProcessScoreRequest {
    @Getter
    @Setter
    @JsonDeserialize(using = EnrollmentIdDeserializer.class)
    private Long enrollmentId;

    @Getter
    private BigDecimal componentScore1;

    @Getter
    private BigDecimal componentScore2;

    @Getter
    private String note;

    private boolean componentScore1Present;
    private boolean componentScore2Present;
    private boolean notePresent;

    @JsonDeserialize(using = ProcessScoreDeserializer.class)
    public void setComponentScore1(BigDecimal componentScore1) {
        this.componentScore1 = componentScore1;
        componentScore1Present = true;
    }

    @JsonDeserialize(using = ProcessScoreDeserializer.class)
    public void setComponentScore2(BigDecimal componentScore2) {
        this.componentScore2 = componentScore2;
        componentScore2Present = true;
    }

    public void setNote(Object note) {
        if (note != null && !(note instanceof String))
            throw new EnrollmentException(
                    ErrorCode.VALIDATION_ERROR,
                    List.of(new FieldErrorDetail("note", "Ghi chú phải là chuỗi hoặc null")));
        this.note = (String) note;
        notePresent = true;
    }

    @JsonIgnore
    public boolean hasComponentScore1() {
        return componentScore1Present;
    }

    @JsonIgnore
    public boolean hasComponentScore2() {
        return componentScore2Present;
    }

    @JsonIgnore
    public boolean hasNote() {
        return notePresent;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object fieldValue) {
        throw new EnrollmentException(
                ErrorCode.VALIDATION_ERROR,
                List.of(new FieldErrorDetail(field, "Trường không được phép cập nhật: " + field)));
    }
}
