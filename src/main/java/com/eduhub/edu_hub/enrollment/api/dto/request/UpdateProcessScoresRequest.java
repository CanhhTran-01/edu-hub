package com.eduhub.edu_hub.enrollment.api.dto.request;

import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProcessScoresRequest {
    private List<UpdateProcessScoreRequest> students;

    @JsonAnySetter
    public void rejectUnknownField(String field, Object fieldValue) {
        throw new EnrollmentException(
                ErrorCode.VALIDATION_ERROR,
                List.of(new FieldErrorDetail(field, "Trường không được phép cập nhật: " + field)));
    }
}
