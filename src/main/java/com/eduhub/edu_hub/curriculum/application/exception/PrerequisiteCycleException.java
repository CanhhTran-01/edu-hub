package com.eduhub.edu_hub.curriculum.application.exception;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

@Getter
public class PrerequisiteCycleException extends BaseException {
    private final List<SubjectSimpleResponse> cycle;

    public PrerequisiteCycleException(List<SubjectSimpleResponse> cycle) {
        super(ErrorCode.PREREQUISITE_CYCLE);
        this.cycle = List.copyOf(cycle);
    }
}
