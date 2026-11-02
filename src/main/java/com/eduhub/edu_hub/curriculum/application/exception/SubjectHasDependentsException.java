package com.eduhub.edu_hub.curriculum.application.exception;

import com.eduhub.edu_hub.curriculum.api.dto.SubjectSimpleResponse;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

@Getter
public class SubjectHasDependentsException extends BaseException {
    private final List<SubjectSimpleResponse> dependentSubjects;

    public SubjectHasDependentsException(List<SubjectSimpleResponse> dependentSubjects) {
        super(ErrorCode.SUBJECT_IN_USE);
        this.dependentSubjects = List.copyOf(dependentSubjects);
    }
}
