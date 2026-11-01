package com.eduhub.edu_hub.shared.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FieldErrorDetail {
    private String field;
    private String message;
}
