package com.eduhub.edu_hub.enrollment.api.dto.request;

import com.eduhub.edu_hub.enrollment.api.serialization.ProcessScoreDeserializer;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Điểm tổng kết, quy đổi, danh tính và hạn khóa luôn do backend xác định. */
@Getter
@Setter
public class UpdateCourseGradeRequest {
    @JsonDeserialize(using = ProcessScoreDeserializer.class)
    private BigDecimal finalExamScore;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object fieldValue) {
        throw new EnrollmentException(
                ErrorCode.VALIDATION_ERROR,
                List.of(new FieldErrorDetail(field, "Trường không được phép cập nhật: " + field)));
    }
}
