package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;

public interface StudentCodeGenerator {
    String generate(AdministrativeClass administrativeClass, int sequenceNumber);

    int nextSequence(AdministrativeClass administrativeClass);
}
